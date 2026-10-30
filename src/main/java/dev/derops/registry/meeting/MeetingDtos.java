package dev.derops.registry.meeting;

import dev.derops.registry.common.Messages;
import dev.derops.registry.reference.ReferenceService.Ref;
import dev.derops.registry.user.UserDtos.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class MeetingDtos {

    private MeetingDtos() {}

    public record MeetingDto(Long id, String title, Ref format, Ref country, Ref organization, LocalDate heldOn,
                             String location, MeetingStatus status, String statusLabel, String summary,
                             UserRef responsible, Instant createdAt, String createdBy, Instant updatedAt,
                             String updatedBy, Long version) {
        public static MeetingDto of(Meeting m, Messages messages) {
            return new MeetingDto(m.getId(), m.getTitle(), Ref.of(m.getFormat()), Ref.of(m.getCountry()),
                    Ref.of(m.getOrganization()), m.getHeldOn(), m.getLocation(), m.getStatus(),
                    messages.enumLabel(m.getStatus()), m.getSummary(), UserRef.of(m.getResponsible()),
                    m.getCreatedAt(), m.getCreatedBy(), m.getUpdatedAt(), m.getUpdatedBy(), m.getVersion());
        }
    }

    /** {@code version} is required on update (optimistic locking) and ignored on create. */
    public record MeetingRequest(
            @NotBlank @Size(max = 500) String title,
            Long formatId,
            Long countryId,
            Long organizationId,
            @NotNull LocalDate heldOn,
            @Size(max = 255) String location,
            MeetingStatus status,
            @Size(max = 4000) String summary,
            Long responsibleId,
            Long version) {}

    public record MeetingFilter(String q, MeetingStatus status, Long countryId, LocalDate from, LocalDate to) {}
}
