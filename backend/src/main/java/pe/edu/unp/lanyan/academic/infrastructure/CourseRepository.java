package pe.edu.unp.lanyan.academic.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.academic.domain.Course;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    boolean existsByCode(String code);
}
