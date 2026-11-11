package pe.edu.unp.lanyan.access.infrastructure;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.edu.unp.lanyan.access.domain.AccessRequest;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {
    @EntityGraph(attributePaths = "resource")
    Page<AccessRequest> findByRequester_Id(UUID requesterId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "resource")
    Page<AccessRequest> findAll(Pageable pageable);

    java.util.List<AccessRequest> findByRequester_IdAndResource_IdAndStatusIn(UUID requesterId, UUID resourceId, Collection<AccessRequest.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessRequest r where r.id = :id")
    Optional<AccessRequest> findForUpdate(UUID id);

    @Query(value = "SELECT nextval('access_request_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
