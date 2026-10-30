package dev.derops.registry.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

    List<AuditEntry> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);

    List<AuditEntry> findByUsernameAndActionOrderByIdDesc(String username, AuditAction action);
}
