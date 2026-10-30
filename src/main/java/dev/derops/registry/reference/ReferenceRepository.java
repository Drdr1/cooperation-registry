package dev.derops.registry.reference;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceRepository extends JpaRepository<ReferenceItem, Long> {

    List<ReferenceItem> findByKindOrderByCode(ReferenceKind kind);

    List<ReferenceItem> findByKindAndActiveTrueOrderByCode(ReferenceKind kind);

    Optional<ReferenceItem> findByKindAndCode(ReferenceKind kind, String code);

    boolean existsByKindAndCode(ReferenceKind kind, String code);
}
