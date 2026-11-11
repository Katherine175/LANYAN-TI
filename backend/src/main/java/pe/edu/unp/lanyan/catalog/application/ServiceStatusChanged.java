package pe.edu.unp.lanyan.catalog.application;

import java.time.Instant;
import java.util.UUID;
import pe.edu.unp.lanyan.catalog.domain.OperationalStatus;

/** Se publica dentro de la misma transacción cuando un servicio cambia de estado operativo. */
public record ServiceStatusChanged(UUID serviceId, String serviceName, OperationalStatus from, OperationalStatus to,
        Instant at, String changedBy) { }
