package dev.derops.registry.notification;

import dev.derops.registry.common.ApiException;
import dev.derops.registry.common.Messages;
import dev.derops.registry.common.PageResponse;
import dev.derops.registry.user.AppUser;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final String LABEL_PREFIX = "label:";

    public record NotificationDto(Long id, NotificationType type, String entityType, Long entityId,
                                  String message, Instant createdAt, boolean read) {}

    private final NotificationRepository repository;
    private final NotificationChannel channel;
    private final Messages messages;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationChannel channel,
                               Messages messages, Clock clock) {
        this.repository = repository;
        this.channel = channel;
        this.messages = messages;
        this.clock = clock;
    }

    /** One-off event (assignment, status change). */
    @Transactional
    public void notifyEvent(AppUser user, NotificationType type, String entityType, Long entityId, List<String> args) {
        create(user, type, entityType, entityId, args, UUID.randomUUID().toString());
    }

    /**
     * Notification that must be sent at most once per {@code dedupeKey}
     * (e.g. one "due soon" reminder per item per day). Returns false if it already exists.
     */
    @Transactional
    public boolean notifyOnce(AppUser user, NotificationType type, String entityType, Long entityId,
                              List<String> args, String dedupeKey) {
        if (repository.existsByUserIdAndTypeAndEntityTypeAndEntityIdAndDedupeKey(
                user.getId(), type, entityType, entityId, dedupeKey)) {
            return false;
        }
        create(user, type, entityType, entityId, args, dedupeKey);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        var result = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
                : repository.findByUserIdOrderByIdDesc(userId, pageable);
        Locale locale = LocaleContextHolder.getLocale();
        return PageResponse.of(result, n -> toDto(n, locale));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("notification", id))
                .markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    /** Argument rendered as the enum's label in the reader's language, not as stored text. */
    public static String label(Enum<?> value) {
        return LABEL_PREFIX + value.getDeclaringClass().getSimpleName() + "." + value.name();
    }

    String render(Notification n, Locale locale) {
        Object args = n.getParams().get("args");
        Object[] values = args instanceof List<?> list ? list.toArray() : new Object[0];
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof String s && s.startsWith(LABEL_PREFIX)) {
                values[i] = messages.get(locale, "enum." + s.substring(LABEL_PREFIX.length()));
            }
        }
        return messages.get(locale, "notification." + n.getType().name(), values);
    }

    private void create(AppUser user, NotificationType type, String entityType, Long entityId,
                        List<String> args, String dedupeKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("args", List.copyOf(args));
        Notification n = new Notification(user.getId(), type, entityType, entityId, params, dedupeKey, clock.instant());
        repository.save(n);
        channel.deliver(user, render(n, Locale.forLanguageTag(user.getPreferredLang())));
    }

    private NotificationDto toDto(Notification n, Locale locale) {
        return new NotificationDto(n.getId(), n.getType(), n.getEntityType(), n.getEntityId(),
                render(n, locale), n.getCreatedAt(), n.getReadAt() != null);
    }
}
