package pe.edu.unp.lanyan.access.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Sistema o recurso sobre el que se conceden permisos. */
@Entity
@Table(name = "access_resources")
public class AccessResource {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false, length = 120) private String owner;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version private Long version;

    protected AccessResource() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public boolean isActive() { return active; }
}
