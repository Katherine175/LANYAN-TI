package pe.edu.unp.lanyan.problems.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.problems.domain.Problem;
import pe.edu.unp.lanyan.tickets.domain.TicketStatus;

public final class ProblemDtos {
    private ProblemDtos() { }

    public record CreateProblemRequest(@NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 4000) String description,
            @NotNull UUID serviceId, @NotNull ServicePriority priority, List<UUID> ticketIds) { }

    public record LinkRequest(@NotNull UUID ticketId) { }

    public record KnownErrorRequest(@NotBlank @Size(max = 4000) String rootCause, @NotBlank @Size(max = 4000) String workaround) { }

    /** {@code resolveLinkedTickets}: resuelve también los incidentes vinculados que sigan abiertos. */
    public record ResolveRequest(@NotBlank @Size(max = 4000) String permanentSolution, Boolean resolveLinkedTickets) {
        public boolean closeLinked() { return Boolean.TRUE.equals(resolveLinkedTickets); }
    }

    public record LinkedIncident(UUID id, String code, String title, TicketStatus status, Instant createdAt) { }

    public record ProblemView(UUID id, String code, String title, String description, UUID serviceId, String serviceName,
            ServicePriority priority, Problem.Status status, String ownerName, String rootCause, String workaround,
            String permanentSolution, Instant createdAt, Instant knownErrorAt, Instant resolvedAt, List<LinkedIncident> incidents) { }

    /** Servicio con incidentes repetidos que todavía no se analizan como problema (gestión proactiva). */
    public record Candidate(UUID serviceId, String serviceName, long incidents, Instant lastIncidentAt) { }

    /** Lo que el técnico necesita ver de un error conocido al atender un ticket. */
    public record KnownError(UUID id, String code, String title, String rootCause, String workaround, Problem.Status status) { }
}
