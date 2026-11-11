package pe.edu.unp.lanyan.academic.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "academic_enrollments")
public class Enrollment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false) private StudentProfile student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false) private CourseSection section;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private EnrollmentStatus status;
    @Column(name = "enrolled_at", nullable = false, updatable = false) private Instant enrolledAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Enrollment() { }

    public Enrollment(StudentProfile student, CourseSection section) {
        this.id = UUID.randomUUID();
        this.student = Objects.requireNonNull(student, "La matrícula requiere un perfil estudiantil.");
        this.section = Objects.requireNonNull(section, "La matrícula requiere una sección.");
        this.status = EnrollmentStatus.ENROLLED;
        this.enrolledAt = Instant.now();
        this.updatedAt = enrolledAt;
    }

    /** El cupo se libera; la fila se conserva como historial. */
    public void cancel() {
        if (status != EnrollmentStatus.ENROLLED) throw new IllegalStateException("Solo se anula una matrícula vigente.");
        this.status = EnrollmentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** Volver a la misma sección reutiliza la fila: la base admite una matrícula por estudiante y sección. */
    public void reactivate() {
        if (status != EnrollmentStatus.CANCELLED) throw new IllegalStateException("Solo se reactiva una matrícula anulada.");
        this.status = EnrollmentStatus.ENROLLED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public StudentProfile getStudent() { return student; }
    public CourseSection getSection() { return section; }
    public EnrollmentStatus getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
