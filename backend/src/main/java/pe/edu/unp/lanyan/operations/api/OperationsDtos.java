package pe.edu.unp.lanyan.operations.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OperationsDtos {
    private OperationsDtos() { }

    public record EventView(UUID id, String code, UUID serviceId, String serviceName, String severity, String source, String title,
            String detail, String status, UUID ticketId, String ticketCode, String createdBy, Instant createdAt,
            String acknowledgedBy, Instant acknowledgedAt, Instant closedAt) { }

    public record CreateEventRequest(UUID serviceId, @NotNull @Pattern(regexp = "INFO|WARNING|EXCEPTION") String severity,
            @NotBlank @Size(max = 160) String title, @Size(max = 2000) String detail) { }

    /**
     * Disponibilidad de los últimos 30 días (reloj de 24 horas): tiempo fuera de servicio frente a la meta del catálogo.
     * {@code mtrsMinutes}: tiempo medio para restaurar el servicio tras una caída.
     */
    public record AvailabilityView(UUID serviceId, String serviceName, String currentStatus, BigDecimal target, BigDecimal availability,
            boolean meetsTarget, int outages, long unavailableMinutes, long degradedMinutes, Long mtrsMinutes) { }

    public record MaintenanceView(UUID id, UUID serviceId, String serviceName, String title, Instant startsAt, Instant endsAt,
            String impact, boolean cancelled, String createdBy) { }

    public record CreateMaintenanceRequest(@NotNull UUID serviceId, @NotBlank @Size(max = 160) String title, @NotNull Instant startsAt,
            @NotNull Instant endsAt, @NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH") String impact) { }
}
