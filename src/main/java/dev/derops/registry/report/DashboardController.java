package dev.derops.registry.report;

import dev.derops.registry.agreement.AgreementRepository;
import dev.derops.registry.agreement.AgreementStatus;
import dev.derops.registry.agreement.ImplementationItemRepository;
import dev.derops.registry.agreement.ItemStatus;
import dev.derops.registry.common.Messages;
import dev.derops.registry.meeting.MeetingRepository;
import dev.derops.registry.reference.ReferenceRepository;
import dev.derops.registry.reference.ReferenceService.Ref;
import dev.derops.registry.user.UserDtos.UserRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard")
public class DashboardController {

    public record StatusCount(AgreementStatus status, String label, long count) {}

    public record CountryCount(Ref country, long count) {}

    public record ExpiringAgreement(Long id, String registrationNumber, String title, LocalDate validUntil, UserRef responsible) {}

    public record Dashboard(long totalAgreements, List<StatusCount> agreementsByStatus, long meetingsThisYear,
                            long itemsOverdue, long itemsDueNext30Days, List<ExpiringAgreement> expiringNext90Days,
                            List<CountryCount> topCountries) {}

    private final AgreementRepository agreements;
    private final ImplementationItemRepository items;
    private final MeetingRepository meetings;
    private final ReferenceRepository references;
    private final Messages messages;
    private final Clock clock;

    public DashboardController(AgreementRepository agreements, ImplementationItemRepository items,
                               MeetingRepository meetings, ReferenceRepository references,
                               Messages messages, Clock clock) {
        this.agreements = agreements;
        this.items = items;
        this.meetings = meetings;
        this.references = references;
        this.messages = messages;
        this.clock = clock;
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAuthority('AGREEMENT_READ')")
    @Transactional(readOnly = true)
    @Operation(summary = "Key figures for the home screen")
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(clock);

        Map<AgreementStatus, Long> counts = new EnumMap<>(AgreementStatus.class);
        for (Object[] row : agreements.countByStatus()) {
            counts.put((AgreementStatus) row[0], (Long) row[1]);
        }
        List<StatusCount> byStatus = new ArrayList<>();
        long total = 0;
        for (AgreementStatus s : AgreementStatus.values()) {
            long c = counts.getOrDefault(s, 0L);
            total += c;
            byStatus.add(new StatusCount(s, messages.enumLabel(s), c));
        }

        List<CountryCount> topCountries = agreements.countByCountry().stream()
                .limit(10)
                .map(row -> new CountryCount(Ref.of(references.findById((Long) row[0]).orElse(null)), (Long) row[1]))
                .toList();

        List<ExpiringAgreement> expiring = agreements
                .findByStatusAndValidUntilBetween(AgreementStatus.IN_FORCE, today, today.plusDays(90)).stream()
                .map(a -> new ExpiringAgreement(a.getId(), a.getRegistrationNumber(), a.getTitle(), a.getValidUntil(),
                        UserRef.of(a.getResponsible())))
                .toList();

        return new Dashboard(
                total,
                byStatus,
                meetings.countByHeldOnBetween(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear())),
                items.countByStatusNotAndDueDateBefore(ItemStatus.DONE, today),
                items.countByStatusNotAndDueDateBetween(ItemStatus.DONE, today, today.plusDays(30)),
                expiring,
                topCountries);
    }
}
