package pe.edu.unp.lanyan.tickets.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.tickets.domain.TicketStatus;

public final class TicketDtos {
    private TicketDtos() { }

    /** Sin prioridad: la fija el servicio elegido. */
    public record CreateTicketRequest(@NotNull UUID serviceId, @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description) { }

    public record StatusRequest(@NotNull TicketStatus status) { }

    public record PriorityRequest(@NotNull ServicePriority priority, @NotBlank @Size(min = 5, max = 300) String reason) { }

    public record ServiceRef(UUID id, String code, String name) { }

    /**
     * Estado de un plazo: ON_TIME y AT_RISK (75 % consumido) mientras corre; MET o MISSED cuando ya ocurrió;
     * BREACHED si venció y aún no ocurre.
     */
    public enum SlaState { ON_TIME, AT_RISK, BREACHED, MET, MISSED }

    public record TicketView(UUID id, String code, String title, String description, ServiceRef service,
            String requesterName, String requesterEmail, String assigneeName, ServicePriority priority, TicketStatus status,
            Instant createdAt, Instant firstResponseDue, Instant resolutionDue, Instant firstRespondedAt, Instant resolvedAt,
            SlaState firstResponseSla, SlaState resolutionSla, int resolutionElapsedPercent, Long version) { }
}
