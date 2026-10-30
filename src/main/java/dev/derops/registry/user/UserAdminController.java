package dev.derops.registry.user;

import dev.derops.registry.common.PageResponse;
import dev.derops.registry.user.UserDtos.CreateUserRequest;
import dev.derops.registry.user.UserDtos.ResetPasswordRequest;
import dev.derops.registry.user.UserDtos.RoleDto;
import dev.derops.registry.user.UserDtos.RoleRequest;
import dev.derops.registry.user.UserDtos.UpdateUserRequest;
import dev.derops.registry.user.UserDtos.UserDto;
import dev.derops.registry.user.UserDtos.UserRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Administration: users and roles")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/api/users/lookup")
    @PreAuthorize("hasAnyAuthority('MEETING_WRITE', 'AGREEMENT_WRITE', 'USER_ADMIN')")
    @Operation(summary = "Search active users, e.g. to pick a responsible person")
    public List<UserRef> lookup(@RequestParam(required = false) String q) {
        return service.lookup(q);
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public PageResponse<UserDto> list(@RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.list(q, page, size);
    }

    @GetMapping("/api/admin/users/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/admin/users/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    @Operation(summary = "Update a user; set active=false to deactivate (takes effect immediately)")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/api/admin/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.newPassword());
    }

    @PostMapping("/api/admin/users/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public void unlock(@PathVariable Long id) {
        service.unlock(id);
    }

    @GetMapping("/api/admin/roles")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public List<RoleDto> roles() {
        return service.roles();
    }

    @PostMapping("/api/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public RoleDto createRole(@Valid @RequestBody RoleRequest request) {
        return service.createRole(request);
    }

    @PutMapping("/api/admin/roles/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public RoleDto updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return service.updateRole(id, request);
    }
}
