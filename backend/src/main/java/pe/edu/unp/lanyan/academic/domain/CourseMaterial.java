package pe.edu.unp.lanyan.academic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "academic_course_materials")
public class CourseMaterial {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false) private CourseSection section;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private MaterialType type;
    @Enumerated(EnumType.STRING) @Column(name = "reference_type", nullable = false, length = 32) private MaterialReferenceType referenceType;
    @Column(nullable = false, length = 2048) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private MaterialStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private Long version;

    protected CourseMaterial() { }
    public CourseMaterial(CourseSection section, String title, String description, MaterialType type,
            MaterialReferenceType referenceType, String reference, MaterialStatus status) {
        this.id = UUID.randomUUID(); this.section = Objects.requireNonNull(section); this.createdAt = Instant.now();
        revise(title, description, type, referenceType, reference, status);
    }
    public void revise(String title, String description, MaterialType type,
            MaterialReferenceType referenceType, String reference, MaterialStatus status) {
        if (type == null || type == MaterialType.LINK && referenceType != MaterialReferenceType.HTTPS_URL)
            throw new IllegalArgumentException("Un enlace requiere una URL HTTPS.");
        String normalizedReference = MaterialReferences.normalize(referenceType, reference);
        this.title = AcademicRules.requiredText(title, 160);
        this.description = AcademicRules.optionalText(description, 2000);
        this.type = type; this.referenceType = referenceType; this.reference = normalizedReference;
        this.status = status == null ? MaterialStatus.DRAFT : status;
        this.updatedAt = Instant.now();
    }
    public void retire() { this.deletedAt = Instant.now(); this.updatedAt = deletedAt; }
    public UUID getId() { return id; }
    public CourseSection getSection() { return section; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public MaterialType getType() { return type; }
    public MaterialReferenceType getReferenceType() { return referenceType; }
    public String getReference() { return reference; }
    public MaterialStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getVersion() { return version; }
}
