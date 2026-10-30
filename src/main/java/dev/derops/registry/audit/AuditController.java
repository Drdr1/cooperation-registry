package dev.derops.registry.audit;

import dev.derops.registry.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit log")
public class AuditController {

    public record AuditEntryDto(Long id, Instant occurredAt, String username, AuditAction action,
                                String entityType, Long entityId, Map<String, Object> details, String clientIp) {
        static AuditEntryDto of(AuditEntry e) {
            return new AuditEntryDto(e.getId(), e.getOccurredAt(), e.getUsername(), e.getAction(),
                    e.getEntityType(), e.getEntityId(), e.getDetails(), e.getClientIp());
        }
    }

    private final AuditRepository repository;

    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Transactional(readOnly = true)
    @Operation(summary = "Search the audit trail (newest first)")
    public PageResponse<AuditEntryDto> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Specification<AuditEntry> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (entityType != null) ps.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) ps.add(cb.equal(root.get("entityId"), entityId));
            if (username != null) ps.add(cb.equal(root.get("username"), username));
            if (action != null) ps.add(cb.equal(root.get("action"), action));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) ps.add(cb.lessThan(root.get("occurredAt"), to));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(repository.findAll(spec, pageable), AuditEntryDto::of);
    }
}
