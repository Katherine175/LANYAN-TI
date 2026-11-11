package pe.edu.unp.lanyan.problems.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.requests.domain.Texts;

/**
 * Problema (ITIL: gestión de problemas). Investigando → error conocido (causa raíz y solución temporal
 * documentadas) → resuelto (solución definitiva aplicada).
 */
@Entity
@Table(name = "problems")
public class Problem {
    public enum Status { INVESTIGATING, KNOWN_ERROR, RESOLVED }

    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 16, updatable = false) private String code;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 4000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id", nullable = false) private ItService service;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ServicePriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(name = "owner_name", nullable = false, length = 120) private String ownerName;
    @Column(name = "root_cause", length = 4000) private String rootCause;
    @Column(length = 4000) private String workaround;
    @Column(name = "permanent_solution", length = 4000) private String permanentSolution;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "known_error_at") private Instant knownErrorAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Problem() { }

    public Problem(String code, String title, String description, ItService service, ServicePriority priority, UserAccount owner, Instant now) {
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code);
        this.title = Texts.required(title, 160, "El título es obligatorio.");
        this.description = Texts.required(description, 4000, "Describe el síntoma y desde cuándo ocurre.");
        this.service = Objects.requireNonNull(service);
        this.priority = Objects.requireNonNull(priority);
        this.owner = Objects.requireNonNull(owner);
        this.ownerName = owner.getFullName();
        this.status = Status.INVESTIGATING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Diagnóstico: con causa raíz y solución temporal pasa a error conocido (se puede actualizar). */
    public void documentKnownError(String rootCause, String workaround, Instant now) {
        if (status == Status.RESOLVED) throw new IllegalStateException("El problema ya está resuelto.");
        this.rootCause = Texts.required(rootCause, 4000, "Indica la causa raíz.");
        this.workaround = Texts.required(workaround, 4000, "Indica la solución temporal para atender los incidentes.");
        if (status == Status.INVESTIGATING) this.knownErrorAt = now;
        this.status = Status.KNOWN_ERROR;
        this.updatedAt = now;
    }

    public void resolve(String permanentSolution, Instant now) {
        if (status != Status.KNOWN_ERROR) throw new IllegalStateException("Primero documenta la causa raíz y la solución temporal.");
        this.permanentSolution = Texts.required(permanentSolution, 4000, "Describe la solución definitiva aplicada.");
        this.status = Status.RESOLVED;
        this.resolvedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItService getService() { return service; }
    public ServicePriority getPriority() { return priority; }
    public Status getStatus() { return status; }
    public String getOwnerName() { return ownerName; }
    public String getRootCause() { return rootCause; }
    public String getWorkaround() { return workaround; }
    public String getPermanentSolution() { return permanentSolution; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getKnownErrorAt() { return knownErrorAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
