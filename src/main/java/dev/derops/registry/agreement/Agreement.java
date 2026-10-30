package dev.derops.registry.agreement;

import dev.derops.registry.meeting.Meeting;
import dev.derops.registry.reference.ReferenceItem;
import dev.derops.registry.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "agreement")
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", nullable = false, unique = true, length = 64)
    private String registrationNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private ReferenceItem type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private ReferenceItem country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private ReferenceItem organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(name = "signed_on")
    private LocalDate signedOn;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgreementStatus status = AgreementStatus.DRAFT;

    @Column(length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_id")
    private AppUser responsible;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "external_verified_at")
    private Instant externalVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ReferenceItem getType() {
        return type;
    }

    public void setType(ReferenceItem type) {
        this.type = type;
    }

    public ReferenceItem getCountry() {
        return country;
    }

    public void setCountry(ReferenceItem country) {
        this.country = country;
    }

    public ReferenceItem getOrganization() {
        return organization;
    }

    public void setOrganization(ReferenceItem organization) {
        this.organization = organization;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public LocalDate getSignedOn() {
        return signedOn;
    }

    public void setSignedOn(LocalDate signedOn) {
        this.signedOn = signedOn;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public AgreementStatus getStatus() {
        return status;
    }

    void setStatus(AgreementStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AppUser getResponsible() {
        return responsible;
    }

    public void setResponsible(AppUser responsible) {
        this.responsible = responsible;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Instant getExternalVerifiedAt() {
        return externalVerifiedAt;
    }

    public void markExternallyVerified(String ref, Instant at) {
        this.externalRef = ref;
        this.externalVerifiedAt = at;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void markCreated(Instant at, String by) {
        this.createdAt = at;
        this.createdBy = by;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void markUpdated(Instant at, String by) {
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public Long getVersion() {
        return version;
    }
}
