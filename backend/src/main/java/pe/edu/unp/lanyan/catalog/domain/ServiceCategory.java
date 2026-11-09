package pe.edu.unp.lanyan.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_categories")
public class ServiceCategory {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String name;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version private Long version;

    protected ServiceCategory() { }

    public ServiceCategory(String name) {
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
