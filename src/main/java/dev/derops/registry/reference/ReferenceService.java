package dev.derops.registry.reference;

import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.common.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceService {

    static final String ENTITY = "REFERENCE";

    /** Compact form embedded in other DTOs. */
    public record Ref(Long id, String code, String name) {
        public static Ref of(ReferenceItem item) {
            return item == null ? null : new Ref(item.getId(), item.getCode(), item.name(LocaleContextHolder.getLocale()));
        }
    }

    public record ReferenceDto(Long id, ReferenceKind kind, String code, String name,
                               String nameUz, String nameRu, String nameEn, boolean active) {
        static ReferenceDto of(ReferenceItem i, Locale locale) {
            return new ReferenceDto(i.getId(), i.getKind(), i.getCode(), i.name(locale),
                    i.getNameUz(), i.getNameRu(), i.getNameEn(), i.isActive());
        }
    }

    public record ReferenceRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String nameUz,
            @NotBlank @Size(max = 255) String nameRu,
            @NotBlank @Size(max = 255) String nameEn,
            Boolean active) {}

    private final ReferenceRepository repository;
    private final AuditService audit;

    public ReferenceService(ReferenceRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReferenceDto> list(ReferenceKind kind, boolean includeInactive) {
        Locale locale = LocaleContextHolder.getLocale();
        List<ReferenceItem> items = includeInactive
                ? repository.findByKindOrderByCode(kind)
                : repository.findByKindAndActiveTrueOrderByCode(kind);
        return items.stream().map(i -> ReferenceDto.of(i, locale)).toList();
    }

    @Transactional
    public ReferenceDto create(ReferenceKind kind, ReferenceRequest req) {
        if (repository.existsByKindAndCode(kind, req.code())) {
            throw ApiException.conflict("error.duplicateCode", req.code());
        }
        ReferenceItem item = new ReferenceItem(kind, req.code(), req.nameUz(), req.nameRu(), req.nameEn());
        if (req.active() != null) {
            item.setActive(req.active());
        }
        repository.save(item);
        audit.record(AuditAction.CREATE, ENTITY, item.getId(), snapshot(item));
        return ReferenceDto.of(item, LocaleContextHolder.getLocale());
    }

    @Transactional
    public ReferenceDto update(ReferenceKind kind, Long id, ReferenceRequest req) {
        ReferenceItem item = require(kind, id);
        Map<String, Object> before = snapshot(item);
        if (!item.getCode().equals(req.code()) && repository.existsByKindAndCode(kind, req.code())) {
            throw ApiException.conflict("error.duplicateCode", req.code());
        }
        item.setCode(req.code());
        item.setNameUz(req.nameUz());
        item.setNameRu(req.nameRu());
        item.setNameEn(req.nameEn());
        if (req.active() != null) {
            item.setActive(req.active());
        }
        audit.record(AuditAction.UPDATE, ENTITY, item.getId(), AuditService.diff(before, snapshot(item)));
        return ReferenceDto.of(item, LocaleContextHolder.getLocale());
    }

    /** Validates that an id points at an active item of the expected kind (null is allowed). */
    @Transactional(readOnly = true)
    public ReferenceItem resolve(ReferenceKind kind, Long id) {
        if (id == null) {
            return null;
        }
        ReferenceItem item = repository.findById(id)
                .filter(i -> i.getKind() == kind)
                .orElseThrow(() -> ApiException.badRequest("error.invalidReference", kind.name(), id));
        if (!item.isActive()) {
            throw ApiException.badRequest("error.inactiveReference", kind.name(), id);
        }
        return item;
    }

    private ReferenceItem require(ReferenceKind kind, Long id) {
        return repository.findById(id)
                .filter(i -> i.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(kind.name(), id));
    }

    private static Map<String, Object> snapshot(ReferenceItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", i.getKind().name());
        m.put("code", i.getCode());
        m.put("nameUz", i.getNameUz());
        m.put("nameRu", i.getNameRu());
        m.put("nameEn", i.getNameEn());
        m.put("active", i.isActive());
        return m;
    }
}
