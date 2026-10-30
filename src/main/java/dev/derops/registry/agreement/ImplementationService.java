package dev.derops.registry.agreement;

import dev.derops.registry.agreement.AgreementDtos.ItemDto;
import dev.derops.registry.agreement.AgreementDtos.ItemRequest;
import dev.derops.registry.agreement.AgreementDtos.ProgressRequest;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.auth.CurrentUser;
import dev.derops.registry.common.ApiException;
import dev.derops.registry.common.Messages;
import dev.derops.registry.notification.NotificationService;
import dev.derops.registry.notification.NotificationType;
import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.Permission;
import dev.derops.registry.user.UserDtos.UserRef;
import dev.derops.registry.user.UserResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation items (obligations) of an agreement and their progress. */
@Service
public class ImplementationService {

    public static final String ENTITY = "ITEM";

    private final ImplementationItemRepository repository;
    private final AgreementRepository agreements;
    private final UserResolver userResolver;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Messages messages;
    private final Clock clock;

    public ImplementationService(ImplementationItemRepository repository, AgreementRepository agreements,
                                 UserResolver userResolver, NotificationService notifications,
                                 AuditService audit, Messages messages, Clock clock) {
        this.repository = repository;
        this.agreements = agreements;
        this.userResolver = userResolver;
        this.notifications = notifications;
        this.audit = audit;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> listForAgreement(Long agreementId) {
        LocalDate today = LocalDate.now(clock);
        return repository.findByAgreementIdOrderByDueDateAsc(agreementId).stream().map(i -> toDto(i, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemDto> listMine(Long userId, boolean openOnly) {
        LocalDate today = LocalDate.now(clock);
        return repository.findByResponsibleIdOrderByDueDateAsc(userId).stream()
                .filter(i -> !openOnly || i.getStatus() != ItemStatus.DONE)
                .map(i -> toDto(i, today))
                .toList();
    }

    @Transactional
    public ItemDto add(Long agreementId, ItemRequest req) {
        Agreement agreement = agreements.findById(agreementId)
                .orElseThrow(() -> ApiException.notFound("agreement", agreementId));
        if (agreement.getStatus().isFinal()) {
            throw ApiException.conflict("error.agreementClosed", messages.enumLabel(agreement.getStatus()));
        }
        ImplementationItem item = new ImplementationItem();
        item.setAgreement(agreement);
        item.setDescription(req.description().trim());
        item.setResponsible(userResolver.active(req.responsibleId()));
        item.setDueDate(req.dueDate());
        repository.save(item);
        audit.record(AuditAction.CREATE, ENTITY, item.getId(), snapshot(item));
        notifyAssigned(item);
        return toDto(item, LocalDate.now(clock));
    }

    @Transactional
    public ItemDto update(Long itemId, ItemRequest req) {
        ImplementationItem item = require(itemId);
        checkVersion(item, req.version());
        Map<String, Object> before = snapshot(item);
        AppUser previous = item.getResponsible();
        item.setDescription(req.description().trim());
        item.setResponsible(userResolver.active(req.responsibleId()));
        item.setDueDate(req.dueDate());
        item.setUpdatedAt(clock.instant());
        audit.record(AuditAction.UPDATE, ENTITY, item.getId(), AuditService.diff(before, snapshot(item)));
        if (!Objects.equals(previous.getId(), item.getResponsible().getId())) {
            notifyAssigned(item);
        }
        repository.flush();
        return toDto(item, LocalDate.now(clock));
    }

    /**
     * Progress update. Users with AGREEMENT_WRITE may update any item; everyone
     * else (ITEM_UPDATE) only the items they are responsible for.
     */
    @Transactional
    public ItemDto updateProgress(Long itemId, ProgressRequest req) {
        ImplementationItem item = require(itemId);
        boolean owner = Objects.equals(item.getResponsible().getId(), CurrentUser.id());
        if (!owner && !CurrentUser.has(Permission.AGREEMENT_WRITE)) {
            throw ApiException.forbidden("error.notResponsible");
        }
        checkVersion(item, req.version());
        Map<String, Object> before = snapshot(item);
        LocalDate today = LocalDate.now(clock);
        if (req.status() == ItemStatus.DONE && item.getStatus() != ItemStatus.DONE) {
            item.setCompletedOn(today);
        } else if (req.status() != ItemStatus.DONE) {
            item.setCompletedOn(null);
        }
        item.setStatus(req.status());
        item.setProgressNote(req.progressNote());
        item.setUpdatedAt(clock.instant());
        audit.record(AuditAction.UPDATE, ENTITY, item.getId(), AuditService.diff(before, snapshot(item)));
        repository.flush();
        return toDto(item, today);
    }

    @Transactional
    public void delete(Long itemId) {
        ImplementationItem item = require(itemId);
        Map<String, Object> snapshot = snapshot(item);
        repository.delete(item);
        audit.record(AuditAction.DELETE, ENTITY, itemId, snapshot);
    }

    private void notifyAssigned(ImplementationItem item) {
        if (!Objects.equals(item.getResponsible().getId(), CurrentUser.id())) {
            notifications.notifyEvent(item.getResponsible(), NotificationType.ITEM_ASSIGNED, ENTITY, item.getId(),
                    List.of(item.getDescription(), item.getAgreement().getRegistrationNumber(), item.getDueDate().toString()));
        }
    }

    private ImplementationItem require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("item", id));
    }

    private static void checkVersion(ImplementationItem item, Long version) {
        if (version == null || !Objects.equals(version, item.getVersion())) {
            throw ApiException.conflict("error.staleVersion");
        }
    }

    ItemDto toDto(ImplementationItem i, LocalDate today) {
        return new ItemDto(i.getId(), i.getAgreement().getId(), i.getAgreement().getRegistrationNumber(),
                i.getDescription(), UserRef.of(i.getResponsible()), i.getDueDate(), i.getStatus(),
                messages.enumLabel(i.getStatus()), i.isOverdue(today), i.getProgressNote(), i.getCompletedOn(),
                i.getVersion());
    }

    private static Map<String, Object> snapshot(ImplementationItem i) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("agreementId", i.getAgreement().getId());
        s.put("description", i.getDescription());
        s.put("responsibleId", i.getResponsible().getId());
        s.put("dueDate", i.getDueDate().toString());
        s.put("status", i.getStatus().name());
        s.put("progressNote", i.getProgressNote());
        s.put("completedOn", i.getCompletedOn() == null ? null : i.getCompletedOn().toString());
        return s;
    }
}
