package pe.edu.unp.lanyan.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pe.edu.unp.lanyan.identity.domain.UserAccount;

@Entity
@Table(name = "academic_student_profiles")
public class StudentProfile {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_academic_student_profiles_user"))
    private UserAccount user;

    @Column(name = "student_code", nullable = false, unique = true, length = 32)
    private String studentCode;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StudentProfile() { }

    public StudentProfile(UserAccount user, String studentCode, LocalDate admissionDate) {
        this.user = Objects.requireNonNull(user, "El perfil requiere una cuenta existente.");
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("El código estudiantil es obligatorio.");
        }
        var normalized = studentCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) throw new IllegalArgumentException("El código estudiantil admite hasta 32 caracteres.");
        this.id = UUID.randomUUID();
        this.studentCode = normalized;
        this.admissionDate = admissionDate;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getStudentCode() { return studentCode; }
    public LocalDate getAdmissionDate() { return admissionDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
