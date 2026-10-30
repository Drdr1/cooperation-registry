package dev.derops.registry.notification;

import dev.derops.registry.agreement.Agreement;
import dev.derops.registry.agreement.AgreementRepository;
import dev.derops.registry.agreement.AgreementService;
import dev.derops.registry.agreement.AgreementStatus;
import dev.derops.registry.agreement.ImplementationItem;
import dev.derops.registry.agreement.ImplementationItemRepository;
import dev.derops.registry.agreement.ImplementationService;
import dev.derops.registry.agreement.ItemStatus;
import dev.derops.registry.config.AppProperties;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily deadline pass. Idempotent: running it twice on the same day sends nothing
 * new, because every reminder carries a dedupe key.
 */
@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);

    public record RunResult(LocalDate date, int dueSoon, int overdue, int expiring, int expired) {}

    private final ImplementationItemRepository items;
    private final AgreementRepository agreements;
    private final AgreementService agreementService;
    private final NotificationService notifications;
    private final AppProperties.Notifications config;

    public DeadlineReminderService(ImplementationItemRepository items, AgreementRepository agreements,
                                   AgreementService agreementService, NotificationService notifications,
                                   AppProperties props) {
        this.items = items;
        this.agreements = agreements;
        this.agreementService = agreementService;
        this.notifications = notifications;
        this.config = props.notifications();
    }

    @Transactional
    public RunResult run(LocalDate today) {
        int dueSoon = 0;
        for (ImplementationItem item : items.findOpenDueBetween(ItemStatus.DONE, today, today.plusDays(config.reminderDays()))) {
            if (notifications.notifyOnce(item.getResponsible(), NotificationType.ITEM_DUE_SOON, ImplementationService.ENTITY,
                    item.getId(), args(item), "due-soon-" + item.getDueDate())) {
                dueSoon++;
            }
        }

        int overdue = 0;
        for (ImplementationItem item : items.findOverdue(ItemStatus.DONE, today)) {
            // First day overdue, then once a week while it stays open.
            long week = ChronoUnit.DAYS.between(item.getDueDate(), today) / 7;
            if (notifications.notifyOnce(item.getResponsible(), NotificationType.ITEM_OVERDUE, ImplementationService.ENTITY,
                    item.getId(), args(item), "overdue-" + item.getDueDate() + "-w" + week)) {
                overdue++;
            }
        }

        int expiring = 0;
        List<Agreement> soonExpiring = agreements.findByStatusAndValidUntilBetween(
                AgreementStatus.IN_FORCE, today, today.plusDays(config.expiryWarningDays()));
        for (Agreement a : soonExpiring) {
            if (a.getResponsible() != null && notifications.notifyOnce(a.getResponsible(), NotificationType.AGREEMENT_EXPIRING,
                    AgreementService.ENTITY, a.getId(), List.of(a.getRegistrationNumber(), a.getValidUntil().toString()),
                    "expiring-" + a.getValidUntil())) {
                expiring++;
            }
        }

        int expired = agreementService.expireOverdue(today);
        RunResult result = new RunResult(today, dueSoon, overdue, expiring, expired);
        log.info("Deadline run: {}", result);
        return result;
    }

    private static List<String> args(ImplementationItem item) {
        return List.of(item.getDescription(), item.getAgreement().getRegistrationNumber(), item.getDueDate().toString());
    }
}
