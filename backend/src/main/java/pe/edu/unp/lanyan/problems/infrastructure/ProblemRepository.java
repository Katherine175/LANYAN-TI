package pe.edu.unp.lanyan.problems.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.edu.unp.lanyan.problems.domain.Problem;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {
    @Override
    @EntityGraph(attributePaths = "service")
    List<Problem> findAll(Sort sort);

    @EntityGraph(attributePaths = "service")
    List<Problem> findByService_IdAndStatusIn(UUID serviceId, List<Problem.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findForUpdate(UUID id);

    @Query(value = "SELECT nextval('problem_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
