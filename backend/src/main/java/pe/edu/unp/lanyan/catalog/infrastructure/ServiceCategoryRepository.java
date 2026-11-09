package pe.edu.unp.lanyan.catalog.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.catalog.domain.ServiceCategory;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
