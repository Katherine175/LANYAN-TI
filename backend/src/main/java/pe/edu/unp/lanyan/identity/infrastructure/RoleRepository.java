package pe.edu.unp.lanyan.identity.infrastructure;

import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.unp.lanyan.identity.domain.Role;
import pe.edu.unp.lanyan.identity.domain.RoleName;

public interface RoleRepository extends JpaRepository<Role, RoleName> {
    @EntityGraph(attributePaths = "permissions")
    Set<Role> findAllByNameIn(Set<RoleName> names);
}
