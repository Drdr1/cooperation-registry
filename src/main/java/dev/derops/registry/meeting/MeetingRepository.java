package dev.derops.registry.meeting;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeetingRepository extends JpaRepository<Meeting, Long>, JpaSpecificationExecutor<Meeting> {

    @Override
    @EntityGraph(attributePaths = {"format", "country", "organization", "responsible"})
    Page<Meeting> findAll(Specification<Meeting> spec, Pageable pageable);

    long countByHeldOnBetween(LocalDate from, LocalDate to);
}
