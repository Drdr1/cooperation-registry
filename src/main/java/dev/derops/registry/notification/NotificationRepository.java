package dev.derops.registry.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTypeAndEntityTypeAndEntityIdAndDedupeKey(
            Long userId, NotificationType type, String entityType, Long entityId, String dedupeKey);

    List<Notification> findByUserIdAndType(Long userId, NotificationType type);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}
