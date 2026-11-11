package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import pe.edu.unp.lanyan.academic.domain.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<StudentProfile> findByUser_Id(UUID userId);

    Optional<StudentProfile> findByStudentCode(String studentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from StudentProfile profile where profile.id = :id")
    Optional<StudentProfile> findForEnrollment(UUID id);
}
