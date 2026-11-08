package pe.edu.unp.lanyan.academic.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "academic_courses")
public class Course {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 80) private String language;
    @Column(length = 80) private String level;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private CourseStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Course() { }

    public Course(String code, String name, String language, String level, String description, CourseStatus status) {
        this.id = UUID.randomUUID();
        this.code = AcademicRules.code(code, 32);
        this.name = AcademicRules.requiredText(name, 160);
        this.language = AcademicRules.requiredText(language, 80);
        this.level = AcademicRules.optionalText(level, 80);
        this.description = AcademicRules.optionalText(description, 2000);
        this.status = status == null ? CourseStatus.ACTIVE : status;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLanguage() { return language; }
    public String getLevel() { return level; }
    public String getDescription() { return description; }
    public CourseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
