package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import pe.edu.unp.lanyan.academic.domain.Enrollment;
import pe.edu.unp.lanyan.academic.domain.EnrollmentStatus;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudent_IdAndSection_Id(UUID studentId, UUID sectionId);
    long countBySection_IdAndStatus(UUID sectionId, EnrollmentStatus status);

    @Override
    @EntityGraph(attributePaths = {"student", "section", "section.course"})
    Page<Enrollment> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"student", "section", "section.course"})
    Optional<Enrollment> findById(UUID id);

    @EntityGraph(attributePaths = {"student", "section", "section.course"})
    Page<Enrollment> findByStudent_Id(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "section", "section.course"})
    Page<Enrollment> findBySection_Id(UUID sectionId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "section", "section.course"})
    Page<Enrollment> findByStudent_IdAndSection_Id(UUID studentId, UUID sectionId, Pageable pageable);
}
