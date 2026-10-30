package dev.derops.registry.meeting;

import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.common.ApiException;
import dev.derops.registry.common.Messages;
import dev.derops.registry.common.PageResponse;
import dev.derops.registry.meeting.MeetingDtos.MeetingDto;
import dev.derops.registry.meeting.MeetingDtos.MeetingFilter;
import dev.derops.registry.meeting.MeetingDtos.MeetingRequest;
import dev.derops.registry.reference.ReferenceItem;
import dev.derops.registry.reference.ReferenceKind;
import dev.derops.registry.reference.ReferenceService;
import dev.derops.registry.user.UserResolver;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    public static final String ENTITY = "MEETING";

    private final MeetingRepository repository;
    private final ReferenceService references;
    private final UserResolver userResolver;
    private final AuditService audit;
    private final Messages messages;
    private final Clock clock;

    public MeetingService(MeetingRepository repository, ReferenceService references, UserResolver userResolver,
                          AuditService audit, Messages messages, Clock clock) {
        this.repository = repository;
        this.references = references;
        this.userResolver = userResolver;
        this.audit = audit;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeetingDto> list(MeetingFilter filter, Pageable pageable) {
        return PageResponse.of(repository.findAll(spec(filter), pageable), m -> MeetingDto.of(m, messages));
    }

    @Transactional(readOnly = true)
    public MeetingDto get(Long id) {
        return MeetingDto.of(require(id), messages);
    }

    @Transactional
    public MeetingDto create(MeetingRequest req) {
        Meeting m = new Meeting();
        apply(m, req);
        m.markCreated(clock.instant(), AuditService.currentUsername());
        repository.save(m);
        audit.record(AuditAction.CREATE, ENTITY, m.getId(), snapshot(m));
        return MeetingDto.of(m, messages);
    }

    @Transactional
    public MeetingDto update(Long id, MeetingRequest req) {
        Meeting m = require(id);
        if (req.version() == null || !Objects.equals(req.version(), m.getVersion())) {
            throw ApiException.conflict("error.staleVersion");
        }
        Map<String, Object> before = snapshot(m);
        apply(m, req);
        m.markUpdated(clock.instant(), AuditService.currentUsername());
        audit.record(AuditAction.UPDATE, ENTITY, m.getId(), AuditService.diff(before, snapshot(m)));
        repository.flush(); // bump @Version now so the response carries it
        return MeetingDto.of(m, messages);
    }

    @Transactional
    public void delete(Long id) {
        Meeting m = require(id);
        if (m.getStatus() == MeetingStatus.HELD) {
            throw ApiException.conflict("error.meetingHeldNotDeletable");
        }
        Map<String, Object> snapshot = snapshot(m);
        repository.delete(m);
        repository.flush(); // surface FK violations (linked agreements) here as 409, not at commit
        audit.record(AuditAction.DELETE, ENTITY, id, snapshot);
    }

    public Meeting require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("meeting", id));
    }

    private void apply(Meeting m, MeetingRequest req) {
        m.setTitle(req.title().trim());
        m.setFormat(references.resolve(ReferenceKind.MEETING_FORMAT, req.formatId()));
        m.setCountry(references.resolve(ReferenceKind.COUNTRY, req.countryId()));
        m.setOrganization(references.resolve(ReferenceKind.ORGANIZATION, req.organizationId()));
        m.setHeldOn(req.heldOn());
        m.setLocation(req.location());
        if (req.status() != null) {
            m.setStatus(req.status());
        }
        m.setSummary(req.summary());
        m.setResponsible(userResolver.activeOrNull(req.responsibleId()));
    }

    static Specification<Meeting> spec(MeetingFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(cb.lower(root.get("title")), "%" + f.q().trim().toLowerCase() + "%"));
            }
            if (f.status() != null) ps.add(cb.equal(root.get("status"), f.status()));
            if (f.countryId() != null) ps.add(cb.equal(root.get("country").get("id"), f.countryId()));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("heldOn"), f.from()));
            if (f.to() != null) ps.add(cb.lessThanOrEqualTo(root.get("heldOn"), f.to()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static Map<String, Object> snapshot(Meeting m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", m.getTitle());
        s.put("formatId", idOf(m.getFormat()));
        s.put("countryId", idOf(m.getCountry()));
        s.put("organizationId", idOf(m.getOrganization()));
        s.put("heldOn", m.getHeldOn() == null ? null : m.getHeldOn().toString());
        s.put("location", m.getLocation());
        s.put("status", m.getStatus().name());
        s.put("summary", m.getSummary());
        s.put("responsibleId", m.getResponsible() == null ? null : m.getResponsible().getId());
        return s;
    }

    private static Long idOf(ReferenceItem item) {
        return item == null ? null : item.getId();
    }
}
