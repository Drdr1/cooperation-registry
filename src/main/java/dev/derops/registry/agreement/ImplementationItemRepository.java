package dev.derops.registry.agreement;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImplementationItemRepository extends JpaRepository<ImplementationItem, Long> {

    @EntityGraph(attributePaths = {"responsible"})
    List<ImplementationItem> findByAgreementIdOrderByDueDateAsc(Long agreementId);

    @EntityGraph(attributePaths = {"agreement", "responsible"})
    List<ImplementationItem> findByResponsibleIdOrderByDueDateAsc(Long responsibleId);

    @Query("""
            select i from ImplementationItem i join fetch i.agreement join fetch i.responsible
            where i.status <> :done and i.dueDate between :from and :to
            order by i.dueDate
            """)
    List<ImplementationItem> findOpenDueBetween(@Param("done") ItemStatus done,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select i from ImplementationItem i join fetch i.agreement join fetch i.responsible
            where i.status <> :done and i.dueDate < :today
            order by i.dueDate
            """)
    List<ImplementationItem> findOverdue(@Param("done") ItemStatus done, @Param("today") LocalDate today);

    @Query("select i from ImplementationItem i join fetch i.agreement join fetch i.responsible order by i.dueDate, i.id")
    List<ImplementationItem> findAllForReport();

    long countByStatusNotAndDueDateBefore(ItemStatus status, LocalDate date);

    long countByStatusNotAndDueDateBetween(ItemStatus status, LocalDate from, LocalDate to);

    long countByAgreementIdAndStatusNot(Long agreementId, ItemStatus status);

    long countByAgreementIdAndStatusNotAndDueDateBefore(Long agreementId, ItemStatus status, LocalDate date);
}
