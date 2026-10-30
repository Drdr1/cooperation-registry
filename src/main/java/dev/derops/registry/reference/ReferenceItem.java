package dev.derops.registry.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

/** Entry of a reference book (country, partner organization, agreement type, meeting format). */
@Entity
@Table(name = "reference_item")
public class ReferenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReferenceKind kind;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "name_uz", nullable = false)
    private String nameUz;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(nullable = false)
    private boolean active = true;

    protected ReferenceItem() {}

    public ReferenceItem(ReferenceKind kind, String code, String nameUz, String nameRu, String nameEn) {
        this.kind = kind;
        this.code = code;
        this.nameUz = nameUz;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
    }

    public String name(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ru" -> nameRu;
            case "en" -> nameEn;
            default -> nameUz;
        };
    }

    public Long getId() {
        return id;
    }

    public ReferenceKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameUz() {
        return nameUz;
    }

    public void setNameUz(String nameUz) {
        this.nameUz = nameUz;
    }

    public String getNameRu() {
        return nameRu;
    }

    public void setNameRu(String nameRu) {
        this.nameRu = nameRu;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
