package dev.derops.registry.agreement;

import dev.derops.registry.agreement.AgreementDtos.AgreementDetail;
import dev.derops.registry.agreement.AgreementDtos.AgreementDto;
import dev.derops.registry.agreement.AgreementDtos.AgreementFilter;
import dev.derops.registry.agreement.AgreementDtos.AgreementRequest;
import dev.derops.registry.agreement.AgreementDtos.ExternalCheckResult;
import dev.derops.registry.agreement.AgreementDtos.StatusChangeDto;
import dev.derops.registry.agreement.AgreementDtos.StatusChangeRequest;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.auth.CurrentUser;
import dev.derops.registry.common.ApiException;
import dev.derops.registry.common.Messages;
import dev.derops.registry.common.PageResponse;
import dev.derops.registry.integration.external.ExternalRegistryClient;
import dev.derops.registry.meeting.MeetingService;
import dev.derops.registry.notification.NotificationService;
import dev.derops.registry.notification.NotificationType;
import dev.derops.registry.reference.ReferenceItem;
import dev.derops.registry.reference.ReferenceKind;
import dev.derops.registry.reference.ReferenceService;
import dev.derops.registry.user.Permission;
import dev.derops.registry.user.UserResolver;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgreementService {

    public static final String ENTITY = "AGREEMENT";

    private final AgreementRepository repository;
    private final StatusChangeRepository history;
    private final ImplementationService items;
    private final ReferenceService references;
    private final MeetingService meetings;
    private final UserResolver userResolver;
    private final NotificationService notifications;
    private final ExternalRegistryClient externalRegistry;
    private final AuditService audit;
    private final Messages messages;
    private final Clock clock;

    public AgreementService(AgreementRepository repository, StatusChangeRepository history,
                            ImplementationService items, ReferenceService references, MeetingService meetings,
                            UserResolver userResolver, NotificationService notifications,
                            ExternalRegistryClient externalRegistry, AuditService audit, Messages messages,
                            Clock clock) {
        this.repository = repository;
        this.history = history;
        this.items = items;
        this.references = references;
        this.meetings = meetings;
        this.userResolver = userResolver;
        this.notifications = notifications;
        this.externalRegistry = externalRegistry;
        this.audit = audit;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AgreementDto> list(AgreementFilter filter, Pageable pageable) {
        return PageResponse.of(repository.findAll(spec(filter), pageable), a -> AgreementDto.of(a, messages));
    }

    @Transactional(readOnly = true)
    public AgreementDetail get(Long id) {
        Agreement a = repository.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("agreement", id));
        List<StatusChangeDto> changes = history.findByAgreementIdOrderByIdAsc(id).stream()
                .map(h -> new StatusChangeDto(h.getFromStatus(), h.getToStatus(), messages.enumLabel(h.getToStatus()),
                        h.getComment(), h.getChangedBy(), h.getChangedAt()))
                .toList();
        return new AgreementDetail(AgreementDto.of(a, messages), items.listForAgreement(id), changes);
    }

    @Transactional
    public AgreementDto create(AgreementRequest req) {
        if (repository.existsByRegistrationNumber(req.registrationNumber())) {
            throw ApiException.conflict("error.duplicateRegistrationNumber", req.registrationNumber());
        }
        Agreement a = new Agreement();
        apply(a, req);
        a.markCreated(clock.instant(), AuditService.currentUsername());
        repository.save(a);
        history.save(new StatusChange(a.getId(), null, a.getStatus(), null, AuditService.currentUsername(), clock.instant()));
        audit.record(AuditAction.CREATE, ENTITY, a.getId(), snapshot(a));
        return AgreementDto.of(a, messages);
    }

    @Transactional
    public AgreementDto update(Long id, AgreementRequest req) {
        Agreement a = require(id);
        if (req.version() == null || !Objects.equals(req.version(), a.getVersion())) {
            throw ApiException.conflict("error.staleVersion");
        }
        if (a.getStatus().isFinal()) {
            throw ApiException.conflict("error.agreementClosed", messages.enumLabel(a.getStatus()));
        }
        if (!a.getRegistrationNumber().equals(req.registrationNumber())
                && repository.existsByRegistrationNumber(req.registrationNumber())) {
            throw ApiException.conflict("error.duplicateRegistrationNumber", req.registrationNumber());
        }
        Map<String, Object> before = snapshot(a);
        apply(a, req);
        a.markUpdated(clock.instant(), AuditService.currentUsername());
        audit.record(AuditAction.UPDATE, ENTITY, a.getId(), AuditService.diff(before, snapshot(a)));
        repository.flush(); // bump @Version now so the response carries it
        return AgreementDto.of(a, messages);
    }

    /**
     * Moves an agreement along its life cycle. Only transitions in
     * {@link AgreementStatus} are allowed; signing needs AGREEMENT_APPROVE;
     * terminating needs a reason.
     */
    @Transactional
    public AgreementDto changeStatus(Long id, StatusChangeRequest req) {
        Agreement a = require(id);
        AgreementStatus from = a.getStatus();
        AgreementStatus to = req.status();
        if (!from.canMoveTo(to)) {
            throw ApiException.conflict("error.invalidTransition", messages.enumLabel(from), messages.enumLabel(to));
        }
        if (to.requiresApproval() && !CurrentUser.has(Permission.AGREEMENT_APPROVE)) {
            throw ApiException.forbidden("error.approvalRequired");
        }
        String comment = req.comment() == null ? null : req.comment().trim();
        if (to == AgreementStatus.TERMINATED && (comment == null || comment.isEmpty())) {
            throw ApiException.badRequest("error.reasonRequired");
        }
        LocalDate today = LocalDate.now(clock);
        if (to == AgreementStatus.SIGNED && a.getSignedOn() == null) {
            a.setSignedOn(today);
        }
        if (to == AgreementStatus.IN_FORCE && a.getSignedOn() == null) {
            throw ApiException.badRequest("error.signedOnRequired");
        }
        transition(a, to, comment, AuditService.currentUsername());
        if (a.getResponsible() != null && !Objects.equals(a.getResponsible().getId(), CurrentUser.id())) {
            notifications.notifyEvent(a.getResponsible(), NotificationType.AGREEMENT_STATUS_CHANGED, ENTITY, a.getId(),
                    List.of(a.getRegistrationNumber(), NotificationService.label(to)));
        }
        repository.flush();
        return AgreementDto.of(a, messages);
    }

    /** Checks the agreement against the external government registry and stores the reference. */
    @Transactional
    public ExternalCheckResult verifyExternal(Long id) {
        Agreement a = require(id);
        Optional<ExternalRegistryClient.ExternalRecord> record;
        try {
            record = externalRegistry.lookup(a.getRegistrationNumber());
        } catch (ExternalRegistryClient.ExternalRegistryException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "error.externalUnavailable");
        }
        Instant now = clock.instant();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("found", record.isPresent());
        record.ifPresent(r -> {
            a.markExternallyVerified(r.externalRef(), now);
            details.put("externalRef", r.externalRef());
            details.put("externalStatus", r.status());
        });
        audit.record(AuditAction.EXTERNAL_CHECK, ENTITY, a.getId(), details);
        return record.map(r -> new ExternalCheckResult(true, r.externalRef(), r.status(), now))
                .orElseGet(() -> new ExternalCheckResult(false, null, null, now));
    }

    /** Called by the daily job: agreements past their end date become EXPIRED. */
    @Transactional
    public int expireOverdue(LocalDate today) {
        List<Agreement> expired = repository.findByStatusAndValidUntilBefore(AgreementStatus.IN_FORCE, today);
        for (Agreement a : expired) {
            transition(a, AgreementStatus.EXPIRED, "validUntil " + a.getValidUntil(), AuditService.SYSTEM_USER);
        }
        return expired.size();
    }

    Agreement require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("agreement", id));
    }

    private void transition(Agreement a, AgreementStatus to, String comment, String actor) {
        AgreementStatus from = a.getStatus();
        Instant now = clock.instant();
        a.setStatus(to);
        a.markUpdated(now, actor);
        history.save(new StatusChange(a.getId(), from, to, comment, actor, now));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.name());
        details.put("to", to.name());
        details.put("comment", comment);
        audit.recordAs(actor, AuditAction.STATUS_CHANGE, ENTITY, a.getId(), details);
    }

    private void apply(Agreement a, AgreementRequest req) {
        if (req.signedOn() != null && req.validUntil() != null && req.validUntil().isBefore(req.signedOn())) {
            throw ApiException.badRequest("error.validUntilBeforeSigned");
        }
        a.setRegistrationNumber(req.registrationNumber().trim());
        a.setTitle(req.title().trim());
        a.setType(references.resolve(ReferenceKind.AGREEMENT_TYPE, req.typeId()));
        a.setCountry(references.resolve(ReferenceKind.COUNTRY, req.countryId()));
        a.setOrganization(references.resolve(ReferenceKind.ORGANIZATION, req.organizationId()));
        a.setMeeting(req.meetingId() == null ? null : meetings.require(req.meetingId()));
        a.setSignedOn(req.signedOn());
        a.setValidUntil(req.validUntil());
        a.setDescription(req.description());
        a.setResponsible(userResolver.activeOrNull(req.responsibleId()));
    }

    public static Specification<Agreement> spec(AgreementFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("registrationNumber")), like)));
            }
            if (f.status() != null) ps.add(cb.equal(root.get("status"), f.status()));
            if (f.countryId() != null) ps.add(cb.equal(root.get("country").get("id"), f.countryId()));
            if (f.typeId() != null) ps.add(cb.equal(root.get("type").get("id"), f.typeId()));
            if (f.responsibleId() != null) ps.add(cb.equal(root.get("responsible").get("id"), f.responsibleId()));
            if (f.signedFrom() != null) ps.add(cb.greaterThanOrEqualTo(root.get("signedOn"), f.signedFrom()));
            if (f.signedTo() != null) ps.add(cb.lessThanOrEqualTo(root.get("signedOn"), f.signedTo()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static Map<String, Object> snapshot(Agreement a) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("registrationNumber", a.getRegistrationNumber());
        s.put("title", a.getTitle());
        s.put("typeId", idOf(a.getType()));
        s.put("countryId", idOf(a.getCountry()));
        s.put("organizationId", idOf(a.getOrganization()));
        s.put("meetingId", a.getMeeting() == null ? null : a.getMeeting().getId());
        s.put("signedOn", a.getSignedOn() == null ? null : a.getSignedOn().toString());
        s.put("validUntil", a.getValidUntil() == null ? null : a.getValidUntil().toString());
        s.put("status", a.getStatus().name());
        s.put("description", a.getDescription());
        s.put("responsibleId", a.getResponsible() == null ? null : a.getResponsible().getId());
        return s;
    }

    private static Long idOf(ReferenceItem item) {
        return item == null ? null : item.getId();
    }
}
