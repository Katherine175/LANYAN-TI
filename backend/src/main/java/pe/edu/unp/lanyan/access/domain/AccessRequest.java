package pe.edu.unp.lanyan.access.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.requests.domain.Texts;

/**
 * Solicitud y concesión de acceso (ITIL: gestión de accesos). Aprobada = acceso vigente,
 * hasta que se revoca o llega su fecha de vencimiento.
 */
@Entity
@Table(name = "access_requests")
public class AccessRequest {
    public enum Level { READ, EDIT, ADMIN }
    public enum Status { PENDING, APPROVED, REJECTED, REVOKED, CANCELLED }

    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 16, updatable = false) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resource_id", nullable = false, updatable = false) private AccessResource resource;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private Level level;
    @Column(nullable = false, length = 2000) private String justification;
    @Column(name = "expires_on") private LocalDate expiresOn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "requester_id", updatable = false) private UserAccount requester;
    @Column(name = "requester_name", nullable = false, length = 120, updatable = false) private String requesterName;
    @Column(name = "requester_email", nullable = false, length = 254, updatable = false) private String requesterEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "decided_by_name", length = 120) private String decidedByName;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", length = 500) private String decisionNote;
    @Column(name = "revoked_by_name", length = 120) private String revokedByName;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoke_reason", length = 500) private String revokeReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected AccessRequest() { }

    public AccessRequest(String code, AccessResource resource, Level level, String justification, LocalDate expiresOn,
            UserAccount requester, Instant now) {
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code);
        this.resource = Objects.requireNonNull(resource);
        this.level = Objects.requireNonNull(level);
        this.justification = Texts.required(justification, 2000, "Explica para qué necesitas el acceso.");
        this.expiresOn = expiresOn;
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

    public void revoke(UserAccount actor, String reason, Instant now) {
        move(Status.APPROVED, Status.REVOKED, now);
        this.revokedByName = actor.getFullName();
        this.revokedAt = now;
        this.revokeReason = Texts.required(reason, 500, "Indica el motivo de la revocación.");
    }

    /** Vigente: aprobado y sin vencer. */
    public boolean isActiveOn(LocalDate day) {
        return status == Status.APPROVED && (expiresOn == null || !expiresOn.isBefore(day));
    }

    private void move(Status expected, Status next, Instant now) {
        if (status != expected) throw new IllegalStateException("La solicitud está en estado " + status + ".");
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public AccessResource getResource() { return resource; }
    public Level getLevel() { return level; }
    public String getJustification() { return justification; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public UserAccount getRequester() { return requester; }
    public String getRequesterName() { return requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public Status getStatus() { return status; }
    public String getDecidedByName() { return decidedByName; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public String getRevokedByName() { return revokedByName; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public Instant getCreatedAt() { return createdAt; }
}
