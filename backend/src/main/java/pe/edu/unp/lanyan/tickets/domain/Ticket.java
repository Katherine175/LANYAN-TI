package pe.edu.unp.lanyan.tickets.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.identity.domain.UserAccount;

/** Incidente o pedido de soporte con sus plazos de atención (ITIL: gestión de incidentes). */
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 16, updatable = false) private String code;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 4000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id", nullable = false, updatable = false) private ItService service;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requester_id", updatable = false) private UserAccount requester;
    @Column(name = "requester_name", nullable = false, length = 120, updatable = false) private String requesterName;
    @Column(name = "requester_email", nullable = false, length = 254, updatable = false) private String requesterEmail;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_id") private UserAccount assignee;
    @Column(name = "assignee_name", length = 120) private String assigneeName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ServicePriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TicketStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "first_response_due", nullable = false) private Instant firstResponseDue;
    @Column(name = "resolution_due", nullable = false) private Instant resolutionDue;
    @Column(name = "first_responded_at") private Instant firstRespondedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Ticket() { }

    public Ticket(String code, String title, String description, ItService service, UserAccount requester,
            ServicePriority priority, Instant createdAt, Instant firstResponseDue, Instant resolutionDue) {
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("El asunto es obligatorio.");
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.service = Objects.requireNonNull(service);
        this.requester = Objects.requireNonNull(requester);
        this.requesterName = requester.getFullName();
        this.requesterEmail = requester.getEmail();
        this.priority = Objects.requireNonNull(priority);
        this.status = TicketStatus.NEW;
        this.createdAt = createdAt;
        this.firstResponseDue = firstResponseDue;
        this.resolutionDue = resolutionDue;
        this.updatedAt = createdAt;
    }

    /** La primera acción de un técnico (tomarlo, moverlo o responderle al solicitante) cuenta como primera respuesta. */
    public void markFirstResponse(Instant now) {
        if (firstRespondedAt == null) {
            firstRespondedAt = now;
            updatedAt = now;
        }
    }

    public void assignTo(UserAccount technician, Instant now) {
        this.assignee = technician;
        this.assigneeName = technician.getFullName();
        markFirstResponse(now);
        if (status == TicketStatus.NEW) status = TicketStatus.IN_PROGRESS;
        this.updatedAt = now;
    }

    public void changeStatus(TicketStatus next, Instant now) {
        if (next == TicketStatus.NEW) throw new IllegalArgumentException("Un ticket atendido no vuelve a Nuevo.");
        markFirstResponse(now);
        // Reabrir un ticket resuelto borra su hora de resolución: el plazo vuelve a correr.
        this.resolvedAt = next == TicketStatus.RESOLVED ? (resolvedAt == null ? now : resolvedAt) : null;
        this.status = next;
        this.updatedAt = now;
    }

    /** Recalcula los plazos desde la creación, con los tiempos de la nueva prioridad. */
    public void reprioritize(ServicePriority next, Instant firstResponseDue, Instant resolutionDue, Instant now) {
        this.priority = Objects.requireNonNull(next);
        this.firstResponseDue = firstResponseDue;
        this.resolutionDue = resolutionDue;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItService getService() { return service; }
    public UserAccount getRequester() { return requester; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public UserAccount getAssignee() { return assignee; }
    public String getAssigneeName() { return assigneeName; }
    public ServicePriority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFirstResponseDue() { return firstResponseDue; }
    public Instant getResolutionDue() { return resolutionDue; }
    public Instant getFirstRespondedAt() { return firstRespondedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getVersion() { return version; }
}
