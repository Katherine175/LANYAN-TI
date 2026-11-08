package pe.edu.unp.lanyan.academic.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import pe.edu.unp.lanyan.academic.domain.*;

public final class AcademicDtos {
    private AcademicDtos() { }

    public record CreateCourseRequest(@NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 80) String language,
            @Size(max = 80) String level, @Size(max = 2000) String description, CourseStatus status) { }

    public record CreateSectionRequest(@NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 40) String academicPeriod, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, @NotNull @Min(1) Integer capacity, SectionStatus status) { }

    public record CreateEnrollmentRequest(@NotNull UUID studentProfileId, @NotNull UUID sectionId) { }

    public record CourseView(UUID id, String code, String name, String language, String level,
            String description, CourseStatus status, Instant createdAt) { }

    public record SectionView(UUID id, UUID courseId, String courseCode, String code, String academicPeriod,
            LocalDate startDate, LocalDate endDate, int capacity, SectionStatus status, Instant createdAt) { }

    public record EnrollmentView(UUID id, UUID studentProfileId, String studentCode, UUID sectionId,
            UUID courseId, EnrollmentStatus status, Instant enrolledAt) { }

    public record StudentCourseView(UUID enrollmentId, EnrollmentStatus enrollmentStatus, Instant enrolledAt,
            CourseView course, SectionView section) { }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
