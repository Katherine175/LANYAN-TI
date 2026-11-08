package pe.edu.unp.lanyan.audit.api;

import java.time.Instant;
import java.util.UUID;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.audit.domain.AuditEvent;

public record AuditEventView(UUID id, Instant occurredAt, AuditAction action, UUID actorUserId, String actorEmail,
        String targetType, String targetId, String remoteAddress, String detail) {
    public static AuditEventView of(AuditEvent event) {
        return new AuditEventView(event.getId(), event.getOccurredAt(), event.getAction(), event.getActorUserId(),
                event.getActorEmail(), event.getTargetType(), event.getTargetId(), event.getRemoteAddress(), event.getDetail());
    }
}
