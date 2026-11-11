package pe.edu.unp.lanyan.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.catalog.domain.SlaPolicy;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, ServicePriority> { }
