package pe.edu.unp.lanyan.access.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.unp.lanyan.access.domain.AccessRequest;

public final class AccessDtos {
    private AccessDtos() { }

    public record ResourceView(UUID id, String name, String description, String owner) { }

    public record CreateAccessRequest(@NotNull UUID resourceId, @NotNull AccessRequest.Level level,
            @NotBlank @Size(max = 2000) String justification, LocalDate expiresOn) { }

    public record AccessDecisionRequest(@NotNull Boolean approved, @Size(max = 500) String note) { }

    public record RevokeRequest(@NotBlank @Size(min = 5, max = 500) String reason) { }

    /** {@code active} = aprobado y sin vencer; {@code expired} = aprobado pero ya vencido. */
    public record AccessRequestView(UUID id, String code, UUID resourceId, String resourceName, String resourceOwner,
            AccessRequest.Level level, String justification, LocalDate expiresOn, String requesterName, String requesterEmail,
            AccessRequest.Status status, boolean active, boolean expired, String decidedByName, Instant decidedAt, String decisionNote,
            String revokedByName, Instant revokedAt, String revokeReason, Instant createdAt) { }
}
