package pe.edu.unp.lanyan.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_permissions")
public class Permission {
    @Id
    @Column(length = 80)
    private String name;

    @Column(nullable = false, length = 200)
    private String description;

    protected Permission() { }

    public String getName() { return name; }
}
