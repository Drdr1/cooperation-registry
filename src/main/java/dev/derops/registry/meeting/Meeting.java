package dev.derops.registry.meeting;

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
@Table(name = "meeting")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "format_id")
    private ReferenceItem format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private ReferenceItem country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private ReferenceItem organization;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeetingStatus status = MeetingStatus.PLANNED;

    @Column(length = 4000)
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_id")
    private AppUser responsible;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ReferenceItem getFormat() {
        return format;
    }

    public void setFormat(ReferenceItem format) {
        this.format = format;
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

    public LocalDate getHeldOn() {
        return heldOn;
    }

    public void setHeldOn(LocalDate heldOn) {
        this.heldOn = heldOn;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public AppUser getResponsible() {
        return responsible;
    }

    public void setResponsible(AppUser responsible) {
        this.responsible = responsible;
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
