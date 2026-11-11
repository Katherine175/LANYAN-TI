package pe.edu.unp.lanyan.requests.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.unp.lanyan.requests.domain.ServiceRequest;

public final class RequestDtos {
    private RequestDtos() { }

    public record CreateServiceRequest(@NotNull UUID serviceId, @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 2000) String justification, LocalDate neededBy) { }

    /** Aprobar admite nota opcional; rechazar la exige (se valida en el servicio). */
    public record DecisionRequest(@NotNull Boolean approved, @Size(max = 500) String note) { }

    public record FulfillRequest(@NotBlank @Size(max = 1000) String note) { }

    public record ServiceRequestView(UUID id, String code, UUID serviceId, String serviceName, String title, String justification,
            LocalDate neededBy, boolean overdue, String requesterName, String requesterEmail, ServiceRequest.Status status,
            String decidedByName, Instant decidedAt, String decisionNote, String assigneeName, Instant fulfilledAt,
            String fulfillmentNote, Instant createdAt) { }
}
