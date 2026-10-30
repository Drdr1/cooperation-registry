package dev.derops.registry.agreement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

    List<StatusChange> findByAgreementIdOrderByIdAsc(Long agreementId);
}
