package pe.edu.unp.lanyan.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "academic_course_sections")
public class CourseSection {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false) private Course course;
    @Column(nullable = false, length = 32) private String code;
    @Column(name = "academic_period", nullable = false, length = 40) private String academicPeriod;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(nullable = false) private int capacity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private SectionStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected CourseSection() { }

    public CourseSection(Course course, String code, String academicPeriod, LocalDate startDate,
            LocalDate endDate, int capacity, SectionStatus status) {
        this.course = Objects.requireNonNull(course, "La sección requiere un curso.");
        this.code = AcademicRules.code(code, 32);
        this.academicPeriod = AcademicRules.code(academicPeriod, 40);
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Las fechas de la sección son inválidas.");
        }
        if (capacity < 1) throw new IllegalArgumentException("El cupo debe ser mayor que cero.");
        this.id = UUID.randomUUID();
        this.startDate = startDate;
        this.endDate = endDate;
        this.capacity = capacity;
        this.status = status == null ? SectionStatus.OPEN : status;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public String getCode() { return code; }
    public String getAcademicPeriod() { return academicPeriod; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getCapacity() { return capacity; }
    public SectionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
