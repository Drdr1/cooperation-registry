package dev.derops.registry.agreement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agreement_status_history")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private AgreementStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private AgreementStatus toStatus;

    @Column(length = 1000)
    private String comment;

    @Column(name = "changed_by", nullable = false, length = 64)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected StatusChange() {}

    public StatusChange(Long agreementId, AgreementStatus fromStatus, AgreementStatus toStatus,
                        String comment, String changedBy, Instant changedAt) {
        this.agreementId = agreementId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getAgreementId() {
        return agreementId;
    }

    public AgreementStatus getFromStatus() {
        return fromStatus;
    }

    public AgreementStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
