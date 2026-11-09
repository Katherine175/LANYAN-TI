package pe.edu.unp.lanyan.catalog.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.catalog.domain.ServiceLifecycle;

public interface ItServiceRepository extends JpaRepository<ItService, UUID> {
    @EntityGraph(attributePaths = "category")
    Page<ItService> findByLifecycleStatus(ServiceLifecycle status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<ItService> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<ItService> findById(UUID id);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query(value = "select nextval('catalog_service_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
