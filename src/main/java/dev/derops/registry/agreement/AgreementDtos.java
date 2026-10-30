package dev.derops.registry.agreement;

import dev.derops.registry.common.Messages;
import dev.derops.registry.meeting.Meeting;
import dev.derops.registry.reference.ReferenceService.Ref;
import dev.derops.registry.user.UserDtos.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AgreementDtos {

    private AgreementDtos() {}

    public record MeetingRef(Long id, String title, LocalDate heldOn) {
        static MeetingRef of(Meeting m) {
            return m == null ? null : new MeetingRef(m.getId(), m.getTitle(), m.getHeldOn());
        }
    }

    public record StatusOption(AgreementStatus status, String label) {}

    public record AgreementDto(Long id, String registrationNumber, String title, Ref type, Ref country,
                               Ref organization, MeetingRef meeting, LocalDate signedOn, LocalDate validUntil,
                               AgreementStatus status, String statusLabel, List<StatusOption> nextStatuses,
                               String description, UserRef responsible, String externalRef,
                               Instant externalVerifiedAt, Instant createdAt, String createdBy,
                               Instant updatedAt, String updatedBy, Long version) {
        static AgreementDto of(Agreement a, Messages messages) {
            List<StatusOption> next = a.getStatus().nextStatuses().stream()
                    .map(s -> new StatusOption(s, messages.enumLabel(s)))
                    .toList();
            return new AgreementDto(a.getId(), a.getRegistrationNumber(), a.getTitle(), Ref.of(a.getType()),
                    Ref.of(a.getCountry()), Ref.of(a.getOrganization()), MeetingRef.of(a.getMeeting()),
                    a.getSignedOn(), a.getValidUntil(), a.getStatus(), messages.enumLabel(a.getStatus()), next,
                    a.getDescription(), UserRef.of(a.getResponsible()), a.getExternalRef(),
                    a.getExternalVerifiedAt(), a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt(),
                    a.getUpdatedBy(), a.getVersion());
        }
    }

    public record StatusChangeDto(AgreementStatus fromStatus, AgreementStatus toStatus, String toLabel,
                                  String comment, String changedBy, Instant changedAt) {}

    public record ItemDto(Long id, Long agreementId, String agreementNumber, String description,
                          UserRef responsible, LocalDate dueDate, ItemStatus status, String statusLabel,
                          boolean overdue, String progressNote, LocalDate completedOn, Long version) {}

    public record AgreementDetail(AgreementDto agreement, List<ItemDto> items, List<StatusChangeDto> history) {}

    public record AgreementRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9./_-]{1,64}$") String registrationNumber,
            @NotBlank @Size(max = 500) String title,
            Long typeId,
            Long countryId,
            Long organizationId,
            Long meetingId,
            LocalDate signedOn,
            LocalDate validUntil,
            @Size(max = 4000) String description,
            Long responsibleId,
            Long version) {}

    public record StatusChangeRequest(@NotNull AgreementStatus status, @Size(max = 1000) String comment) {}

    public record ItemRequest(
            @NotBlank @Size(max = 2000) String description,
            @NotNull Long responsibleId,
            @NotNull LocalDate dueDate,
            Long version) {}

    public record ProgressRequest(@NotNull ItemStatus status, @Size(max = 2000) String progressNote, @NotNull Long version) {}

    public record ExternalCheckResult(boolean found, String externalRef, String externalStatus, Instant checkedAt) {}

    public record AgreementFilter(String q, AgreementStatus status, Long countryId, Long typeId,
                                  Long responsibleId, LocalDate signedFrom, LocalDate signedTo) {}
}
