package dev.derops.registry.user;

import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.common.ApiException;
import dev.derops.registry.common.PageResponse;
import dev.derops.registry.user.UserDtos.CreateUserRequest;
import dev.derops.registry.user.UserDtos.RoleDto;
import dev.derops.registry.user.UserDtos.RoleRequest;
import dev.derops.registry.user.UserDtos.UpdateUserRequest;
import dev.derops.registry.user.UserDtos.UserDto;
import dev.derops.registry.user.UserDtos.UserRef;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    static final String USER = "USER";
    static final String ROLE = "ROLE";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("username"));
        return PageResponse.of(users.search(q == null ? "" : q.trim(), pageable), UserDto::of);
    }

    @Transactional(readOnly = true)
    public List<UserRef> lookup(String q) {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("fullName"));
        return users.search(q == null ? "" : q.trim(), pageable).getContent().stream()
                .filter(AppUser::isActive)
                .map(UserRef::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.of(require(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("error.duplicateUsername", req.username());
        }
        if (blankToNull(req.oneidSubject()) != null && users.existsByOneidSubject(req.oneidSubject())) {
            throw ApiException.conflict("error.duplicateOneId");
        }
        if (req.password() == null && blankToNull(req.oneidSubject()) == null) {
            throw ApiException.badRequest("error.noLoginMethod");
        }
        AppUser user = new AppUser(req.username(), req.fullName());
        user.setEmail(req.email());
        user.setDepartment(req.department());
        user.setOneidSubject(blankToNull(req.oneidSubject()));
        if (req.preferredLang() != null) {
            user.setPreferredLang(req.preferredLang());
        }
        if (req.password() != null) {
            user.setPasswordHash(encoder.encode(req.password()));
        }
        user.getRoles().addAll(resolveRoles(req.roles()));
        users.save(user);
        audit.record(AuditAction.CREATE, USER, user.getId(), snapshot(user));
        return UserDto.of(user);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req) {
        AppUser user = require(id);
        if (!Objects.equals(user.getVersion(), req.version())) {
            throw ApiException.conflict("error.staleVersion");
        }
        String subject = blankToNull(req.oneidSubject());
        if (subject != null && !subject.equals(user.getOneidSubject()) && users.existsByOneidSubject(subject)) {
            throw ApiException.conflict("error.duplicateOneId");
        }
        Map<String, Object> before = snapshot(user);
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setDepartment(req.department());
        user.setOneidSubject(subject);
        if (req.preferredLang() != null) {
            user.setPreferredLang(req.preferredLang());
        }
        user.setActive(req.active());
        user.getRoles().clear();
        user.getRoles().addAll(resolveRoles(req.roles()));
        audit.record(AuditAction.UPDATE, USER, user.getId(), AuditService.diff(before, snapshot(user)));
        users.flush();
        return UserDto.of(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = require(id);
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        audit.record(AuditAction.UPDATE, USER, user.getId(), Map.of("password", "reset"));
    }

    @Transactional
    public void unlock(Long id) {
        AppUser user = require(id);
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        audit.record(AuditAction.UPDATE, USER, user.getId(), Map.of("lock", "cleared"));
    }

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roles.findAllWithPermissions().stream().map(RoleDto::of).toList();
    }

    @Transactional
    public RoleDto createRole(RoleRequest req) {
        if (roles.findByCode(req.code()).isPresent()) {
            throw ApiException.conflict("error.duplicateCode", req.code());
        }
        Role role = new Role(req.code(), req.name());
        role.setPermissions(req.permissions());
        roles.save(role);
        audit.record(AuditAction.CREATE, ROLE, role.getId(), roleSnapshot(role));
        return RoleDto.of(role);
    }

    /** Permission changes apply to logged-in users on their next request (see UserJwtAuthenticationConverter). */
    @Transactional
    public RoleDto updateRole(Long id, RoleRequest req) {
        Role role = roles.findById(id).orElseThrow(() -> ApiException.notFound("role", id));
        if (!role.getCode().equals(req.code())) {
            throw ApiException.badRequest("error.roleCodeImmutable");
        }
        Map<String, Object> before = roleSnapshot(role);
        role.setName(req.name());
        role.setPermissions(req.permissions());
        audit.record(AuditAction.UPDATE, ROLE, role.getId(), AuditService.diff(before, roleSnapshot(role)));
        return RoleDto.of(role);
    }

    private AppUser require(Long id) {
        return users.findWithRolesById(id).orElseThrow(() -> ApiException.notFound("user", id));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> result = new HashSet<>();
        for (String code : codes) {
            result.add(roles.findByCode(code).orElseThrow(() -> ApiException.badRequest("error.unknownRole", code)));
        }
        return result;
    }

    private static Map<String, Object> snapshot(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("department", u.getDepartment());
        m.put("oneidSubject", u.getOneidSubject());
        m.put("preferredLang", u.getPreferredLang());
        m.put("active", u.isActive());
        m.put("roles", u.getRoles().stream().map(Role::getCode).sorted().toList());
        return m;
    }

    private static Map<String, Object> roleSnapshot(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("permissions", r.getPermissions().stream().map(Enum::name).sorted().toList());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
