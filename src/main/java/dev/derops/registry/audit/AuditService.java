package dev.derops.registry.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit entries in the caller's transaction, so a change and its audit
 * record are committed (or rolled back) together.
 */
@Service
public class AuditService {

    public static final String SYSTEM_USER = "system";

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditAction action, String entityType, Long entityId, Map<String, Object> details) {
        recordAs(currentUsername(), action, entityType, entityId, details);
    }

    @Transactional
    public void recordAs(String username, AuditAction action, String entityType, Long entityId, Map<String, Object> details) {
        repository.save(new AuditEntry(Instant.now(clock), username, action, entityType, entityId, details, clientIp()));
    }

    /** Field-level diff: {@code {field: {from: x, to: y}}} for every field whose value changed. */
    public static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object old = before.get(e.getKey());
            if (!Objects.equals(old, e.getValue())) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", old);
                change.put("to", e.getValue());
                changes.put(e.getKey(), change);
            }
        }
        return changes;
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return SYSTEM_USER;
        }
        return auth.getName();
    }

    private static String clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest request = sra.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
