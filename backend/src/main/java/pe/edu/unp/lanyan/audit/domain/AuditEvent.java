package pe.edu.unp.lanyan.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {
    @Id private UUID id;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private AuditAction action;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "actor_email", length = 254) private String actorEmail;
    @Column(name = "target_type", length = 64) private String targetType;
    @Column(name = "target_id", length = 64) private String targetId;
    @Column(name = "remote_address", length = 64) private String remoteAddress;
    @Column(length = 500) private String detail;

    protected AuditEvent() { }

    public AuditEvent(AuditAction action, UUID actorUserId, String actorEmail, String targetType,
            String targetId, String remoteAddress, String detail) {
        this.id = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.targetType = targetType;
        this.targetId = targetId;
        this.remoteAddress = remoteAddress;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public AuditAction getAction() { return action; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getRemoteAddress() { return remoteAddress; }
    public String getDetail() { return detail; }
}
