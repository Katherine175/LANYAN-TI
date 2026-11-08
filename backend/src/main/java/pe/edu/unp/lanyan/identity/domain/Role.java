package pe.edu.unp.lanyan.identity.domain;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_roles")
public class Role {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RoleName name;

    @Column(nullable = false, length = 200)
    private String description;

    @ManyToMany
    @JoinTable(name = "identity_role_permissions",
            joinColumns = @JoinColumn(name = "role_name"),
            inverseJoinColumns = @JoinColumn(name = "permission_name"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() { }

    public RoleName getName() { return name; }
    public Set<Permission> getPermissions() { return Set.copyOf(permissions); }
}
