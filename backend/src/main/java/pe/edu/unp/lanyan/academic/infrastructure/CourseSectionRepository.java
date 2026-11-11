package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import pe.edu.unp.lanyan.academic.domain.CourseSection;

public interface CourseSectionRepository extends JpaRepository<CourseSection, UUID> {
    boolean existsByCourse_IdAndAcademicPeriodAndCode(UUID courseId, String period, String code);

    @EntityGraph(attributePaths = "course")
    Page<CourseSection> findByCourse_Id(UUID courseId, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    java.util.List<CourseSection> findByCourse_StatusAndStatus(pe.edu.unp.lanyan.academic.domain.CourseStatus courseStatus,
            pe.edu.unp.lanyan.academic.domain.SectionStatus status);

    @Override
    @EntityGraph(attributePaths = "course")
    Optional<CourseSection> findById(UUID id);

    // El bloqueo de fila serializa la comprobación de cupos y la inserción de matrículas.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from CourseSection section where section.id = :id")
    Optional<CourseSection> findForEnrollment(UUID id);

    // All schedule/material mutations lock the parent before reading mutable content.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from CourseSection section where section.id = :id")
    Optional<CourseSection> findForAcademicContent(UUID id);
}
