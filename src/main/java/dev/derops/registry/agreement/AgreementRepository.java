package dev.derops.registry.agreement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AgreementRepository extends JpaRepository<Agreement, Long>, JpaSpecificationExecutor<Agreement> {

    @Override
    @EntityGraph(attributePaths = {"type", "country", "organization", "meeting", "responsible"})
    Page<Agreement> findAll(Specification<Agreement> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"type", "country", "organization", "meeting", "responsible"})
    List<Agreement> findAll(Specification<Agreement> spec, Sort sort);

    @EntityGraph(attributePaths = {"type", "country", "organization", "meeting", "responsible"})
    Optional<Agreement> findWithDetailsById(Long id);

    Optional<Agreement> findByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("select a.status, count(a) from Agreement a group by a.status")
    List<Object[]> countByStatus();

    @Query("select a.country.id, count(a) from Agreement a where a.country is not null group by a.country.id order by count(a) desc")
    List<Object[]> countByCountry();

    @EntityGraph(attributePaths = {"responsible"})
    List<Agreement> findByStatusAndValidUntilBetween(AgreementStatus status, LocalDate from, LocalDate to);

    List<Agreement> findByStatusAndValidUntilBefore(AgreementStatus status, LocalDate date);
}
