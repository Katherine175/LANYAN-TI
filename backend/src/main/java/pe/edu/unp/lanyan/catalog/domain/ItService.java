package pe.edu.unp.lanyan.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "catalog_services")
public class ItService {
    @Id private UUID id;
    @Column(nullable = false, length = 16, updatable = false) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 1000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false) private ServiceCategory category;
    @Column(nullable = false, length = 120) private String owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ServiceIcon icon;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false, length = 16) private ServiceLifecycle lifecycleStatus;
    @Enumerated(EnumType.STRING) @Column(name = "operational_status", nullable = false, length = 16) private OperationalStatus operationalStatus;
    @Column(name = "availability_target", nullable = false, precision = 5, scale = 2) private BigDecimal availabilityTarget;
    @Column(name = "response_target_minutes", nullable = false) private int responseTargetMinutes;
    @Enumerated(EnumType.STRING) @Column(name = "default_priority", nullable = false, length = 16) private ServicePriority defaultPriority = ServicePriority.MEDIUM;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected ItService() { }

    public ItService(String code, String name, String description, ServiceCategory category, String owner, ServiceIcon icon,
            ServiceLifecycle lifecycle, BigDecimal availabilityTarget, int responseTargetMinutes) {
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code);
        this.createdAt = Instant.now();
        this.operationalStatus = OperationalStatus.OPERATIONAL;
        revise(name, description, category, owner, icon, lifecycle, availabilityTarget, responseTargetMinutes);
    }

    public void revise(String name, String description, ServiceCategory category, String owner, ServiceIcon icon,
            ServiceLifecycle lifecycle, BigDecimal availabilityTarget, int responseTargetMinutes) {
        this.name = name.trim();
        this.description = description.trim();
        this.category = Objects.requireNonNull(category);
        this.owner = owner.trim();
        this.icon = Objects.requireNonNull(icon);
        this.lifecycleStatus = lifecycle == null ? ServiceLifecycle.DRAFT : lifecycle;
        this.availabilityTarget = Objects.requireNonNull(availabilityTarget);
        this.responseTargetMinutes = responseTargetMinutes;
        this.updatedAt = Instant.now();
    }

    /** Prioridad con la que nacen sus tickets; Crítica nunca es automática. */
    public void changeDefaultPriority(ServicePriority priority, int firstResponseMinutes) {
        if (priority == ServicePriority.CRITICAL) throw new IllegalArgumentException("La prioridad Crítica solo la asigna un técnico.");
        this.defaultPriority = java.util.Objects.requireNonNull(priority);
        // La respuesta objetivo del catálogo es la misma que se aplica a sus tickets.
        this.responseTargetMinutes = firstResponseMinutes;
        this.updatedAt = Instant.now();
    }

    public void changeOperationalStatus(OperationalStatus status) {
        this.operationalStatus = Objects.requireNonNull(status);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ServiceCategory getCategory() { return category; }
    public String getOwner() { return owner; }
    public ServiceIcon getIcon() { return icon; }
    public ServiceLifecycle getLifecycleStatus() { return lifecycleStatus; }
    public OperationalStatus getOperationalStatus() { return operationalStatus; }
    public BigDecimal getAvailabilityTarget() { return availabilityTarget; }
    public int getResponseTargetMinutes() { return responseTargetMinutes; }
    public ServicePriority getDefaultPriority() { return defaultPriority; }
    public Long getVersion() { return version; }
}
