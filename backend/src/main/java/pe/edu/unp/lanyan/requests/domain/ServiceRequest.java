package pe.edu.unp.lanyan.requests.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.identity.domain.UserAccount;

/**
 * Solicitud de servicio (ITIL: cumplimiento de solicitudes). Flujo:
 * pendiente → aprobada o rechazada → en atención → atendida. El solicitante cancela mientras esté pendiente.
 */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {
    public enum Status { PENDING, APPROVED, REJECTED, IN_PROGRESS, FULFILLED, CANCELLED }

    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 16, updatable = false) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id", nullable = false, updatable = false) private ItService service;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 2000) private String justification;
    @Column(name = "needed_by") private LocalDate neededBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requester_id", updatable = false) private UserAccount requester;
    @Column(name = "requester_name", nullable = false, length = 120, updatable = false) private String requesterName;
    @Column(name = "requester_email", nullable = false, length = 254, updatable = false) private String requesterEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "decided_by_name", length = 120) private String decidedByName;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", length = 500) private String decisionNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_id") private UserAccount assignee;
    @Column(name = "assignee_name", length = 120) private String assigneeName;
    @Column(name = "fulfilled_at") private Instant fulfilledAt;
    @Column(name = "fulfillment_note", length = 1000) private String fulfillmentNote;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected ServiceRequest() { }

    public ServiceRequest(String code, ItService service, String title, String justification, LocalDate neededBy,
            UserAccount requester, Instant now) {
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code);
        this.service = Objects.requireNonNull(service);
        this.title = Texts.required(title, 160, "El asunto es obligatorio.");
        this.justification = Texts.required(justification, 2000, "Explica para qué necesitas el servicio.");
        this.neededBy = neededBy;
        this.requester = Objects.requireNonNull(requester);
        this.requesterName = requester.getFullName();
        this.requesterEmail = requester.getEmail();
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void cancel(Instant now) { move(Status.PENDING, Status.CANCELLED, now); }

    public void decide(boolean approved, UserAccount approver, String note, Instant now) {
        move(Status.PENDING, approved ? Status.APPROVED : Status.REJECTED, now);
        this.decidedByName = approver.getFullName();
        this.decidedAt = now;
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }

    public void assignTo(UserAccount technician, Instant now) {
        move(Status.APPROVED, Status.IN_PROGRESS, now);
        this.assignee = technician;
        this.assigneeName = technician.getFullName();
    }

    public void fulfill(String note, Instant now) {
        move(Status.IN_PROGRESS, Status.FULFILLED, now);
        this.fulfilledAt = now;
        this.fulfillmentNote = Texts.required(note, 1000, "Describe qué se entregó o realizó.");
    }

    private void move(Status expected, Status next, Instant now) {
        if (status != expected) throw new IllegalStateException("La solicitud está en estado " + status + ".");
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public ItService getService() { return service; }
    public String getTitle() { return title; }
    public String getJustification() { return justification; }
    public LocalDate getNeededBy() { return neededBy; }
    public UserAccount getRequester() { return requester; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public Status getStatus() { return status; }
    public String getDecidedByName() { return decidedByName; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public String getAssigneeName() { return assigneeName; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public String getFulfillmentNote() { return fulfillmentNote; }
    public Instant getCreatedAt() { return createdAt; }
}
