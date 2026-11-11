package pe.edu.unp.lanyan.access.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.access.domain.AccessResource;

public interface AccessResourceRepository extends JpaRepository<AccessResource, UUID> {
    List<AccessResource> findByActiveTrueOrderByName();
}
