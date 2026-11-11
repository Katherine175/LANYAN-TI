package pe.edu.unp.lanyan.requests.infrastructure;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.edu.unp.lanyan.requests.domain.ServiceRequest;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    @EntityGraph(attributePaths = "service")
    Page<ServiceRequest> findByRequester_Id(UUID requesterId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "service")
    Page<ServiceRequest> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> findForUpdate(UUID id);

    @Query(value = "SELECT nextval('service_request_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
