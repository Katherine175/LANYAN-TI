package pe.edu.unp.lanyan.operations.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.application.ServiceStatusChanged;
import pe.edu.unp.lanyan.catalog.domain.OperationalStatus;
import pe.edu.unp.lanyan.operations.api.OperationsDtos.*;
import pe.edu.unp.lanyan.shared.application.ApiException;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.CreateTicketRequest;
import pe.edu.unp.lanyan.tickets.application.TicketService;

/**
 * Gestión de eventos (ITIL: Operación del servicio) y de la disponibilidad (ITIL: Diseño del servicio;
 * ISO/IEC 20000-1, 8.7.1). Cada cambio de estado de un servicio queda en su historial y genera un evento.
 */
@Service
@Transactional(readOnly = true)
public class OperationsService {
    static final Duration WINDOW = Duration.ofDays(30);

    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public OperationsService(JdbcTemplate jdbc, TicketService tickets, AuditService audit) {
        this.jdbc = jdbc; this.tickets = tickets; this.audit = audit;
    }

    /** Monitoreo: el cambio de estado se registra y produce un evento automático en la misma transacción. */
    @EventListener
    @Transactional
    public void onStatusChanged(ServiceStatusChanged change) {
        jdbc.update("INSERT INTO service_status_changes (id, service_id, from_status, to_status, changed_at, changed_by) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), change.serviceId(), change.from().name(), change.to().name(), Timestamp.from(change.at()), change.changedBy());
        var severity = switch (change.to()) { case UNAVAILABLE -> "EXCEPTION"; case DEGRADED -> "WARNING"; case OPERATIONAL -> "INFO"; };
        var title = switch (change.to()) {
            case UNAVAILABLE -> change.serviceName() + " no disponible";
            case DEGRADED -> change.serviceName() + " funciona con fallas";
            case OPERATIONAL -> change.serviceName() + " restablecido";
        };
        // Al restablecerse, los eventos automáticos abiertos de ese servicio quedan cerrados.
        if (change.to() == OperationalStatus.OPERATIONAL) {
            jdbc.update("UPDATE operational_events SET status = 'CLOSED', closed_at = ? WHERE service_id = ? AND source = 'AUTOMATIC' AND status <> 'CLOSED'",
                    Timestamp.from(change.at()), change.serviceId());
        }
        insertEvent(change.serviceId(), severity, "AUTOMATIC", title, "Estado operativo: " + change.from() + " a " + change.to() + ".",
                change.changedBy(), change.at(), change.to() == OperationalStatus.OPERATIONAL ? "CLOSED" : "OPEN");
    }

    @PreAuthorize("hasAuthority('operations:manage')")
    public List<EventView> events() {
        return jdbc.query(EVENT_SELECT + " ORDER BY e.created_at DESC LIMIT 100", (rs, i) -> event(rs));
    }

    @Transactional
    @PreAuthorize("hasAuthority('operations:manage')")
    public EventView register(CreateEventRequest body) {
        if (body.serviceId() != null) requireService(body.serviceId());
        var id = insertEvent(body.serviceId(), body.severity(), "MANUAL", body.title().trim(),
                body.detail() == null || body.detail().isBlank() ? null : body.detail().trim(), actor(), Instant.now(clock), "OPEN");
        var view = find(id);
        audit.record(AuditAction.EVENT_REGISTERED, "EVENT", id.toString(), view.code() + "; " + body.severity());
        return view;
    }

    @Transactional
    @PreAuthorize("hasAuthority('operations:manage')")
    public EventView acknowledge(UUID id) {
        if (jdbc.update("UPDATE operational_events SET status = 'ACKNOWLEDGED', acknowledged_by = ?, acknowledged_at = ?, version = version + 1 WHERE id = ? AND status = 'OPEN'",
                actor(), Timestamp.from(Instant.now(clock)), id) == 0) throw transition(id);
        return find(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('operations:manage')")
    public EventView close(UUID id) {
        if (jdbc.update("UPDATE operational_events SET status = 'CLOSED', closed_at = ?, version = version + 1 WHERE id = ? AND status <> 'CLOSED'",
                Timestamp.from(Instant.now(clock)), id) == 0) throw transition(id);
        audit.record(AuditAction.EVENT_CLOSED, "EVENT", id.toString(), find(id).code());
        return find(id);
    }

    /** Correlación: una excepción que afecta a los usuarios se convierte en incidente. */
    @Transactional
    @PreAuthorize("hasAuthority('operations:manage') and hasAuthority('tickets:create') and #actorId == authentication.principal.id")
    public EventView createIncident(UUID id, UUID actorId) {
        var event = find(id);
        if (event.ticketId() != null) throw new ApiException(HttpStatus.CONFLICT, "EVENT_HAS_INCIDENT", "Este evento ya tiene el incidente " + event.ticketCode() + ".");
        if (event.serviceId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "EVENT_WITHOUT_SERVICE", "El evento no indica un servicio afectado.");
        if ("CLOSED".equals(event.status())) throw transition(id);
        var ticket = tickets.create(actorId, new CreateTicketRequest(event.serviceId(), "[" + event.code() + "] " + event.title(),
                event.detail() == null ? "Incidente creado desde el evento " + event.code() + "." : event.detail()));
        jdbc.update("UPDATE operational_events SET ticket_id = ?, status = 'ACKNOWLEDGED', acknowledged_by = COALESCE(acknowledged_by, ?), "
                + "acknowledged_at = COALESCE(acknowledged_at, ?), version = version + 1 WHERE id = ?",
                ticket.id(), actor(), Timestamp.from(Instant.now(clock)), id);
        audit.record(AuditAction.EVENT_INCIDENT_CREATED, "EVENT", id.toString(), event.code() + "; ticket=" + ticket.code());
        return find(id);
    }

    @PreAuthorize("hasAuthority('operations:manage')")
    public List<AvailabilityView> availability() {
        var now = Instant.now(clock);
        var windowStart = now.minus(WINDOW);
        var services = jdbc.query("""
                SELECT id, name, operational_status, availability_target, created_at FROM catalog_services
                WHERE lifecycle_status = 'PUBLISHED' ORDER BY name
                """, (rs, i) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("operational_status"),
                rs.getBigDecimal("availability_target"), rs.getTimestamp("created_at").toInstant()});
        var result = new ArrayList<AvailabilityView>();
        for (var row : services) {
            var serviceId = (UUID) row[0];
            var changes = jdbc.query("SELECT from_status, to_status, changed_at FROM service_status_changes WHERE service_id = ? ORDER BY changed_at",
                    (rs, i) -> new Object[] {rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()}, serviceId);
            result.add(measure(serviceId, (String) row[1], (String) row[2], (BigDecimal) row[3], (Instant) row[4], changes, windowStart, now));
        }
        return result;
    }

    /** Recorre el historial como tramos de estado y suma el tiempo de cada uno dentro de la ventana. */
    public static AvailabilityView measure(UUID serviceId, String name, String current, BigDecimal target, Instant createdAt,
            List<Object[]> changes, Instant windowStart, Instant now) {
        var start = createdAt.isAfter(windowStart) ? createdAt : windowStart;
        String status = null;
        for (var c : changes) if (!((Instant) c[2]).isAfter(start)) status = (String) c[1];
        if (status == null) status = changes.stream().filter(c -> ((Instant) c[2]).isAfter(start)).map(c -> (String) c[0]).findFirst().orElse(current);
        long unavailable = 0, degraded = 0, restoreTotal = 0;
        int outages = 0, restores = 0;
        var t = start;
        for (var c : changes) {
            var at = (Instant) c[2];
            if (!at.isAfter(start) || at.isAfter(now)) continue;
            long seconds = Duration.between(t, at).toSeconds();
            if ("UNAVAILABLE".equals(status)) { unavailable += seconds; restoreTotal += seconds; restores++; }
            if ("DEGRADED".equals(status)) degraded += seconds;
            if ("UNAVAILABLE".equals(c[1])) outages++;
            status = (String) c[1];
            t = at;
        }
        long tail = Duration.between(t, now).toSeconds();
        if ("UNAVAILABLE".equals(status)) unavailable += tail;
        if ("DEGRADED".equals(status)) degraded += tail;
        long total = Math.max(1, Duration.between(start, now).toSeconds());
        var availability = BigDecimal.valueOf(100.0 * (total - unavailable) / total).setScale(2, RoundingMode.HALF_UP);
        Long mtrs = restores == 0 ? null : Math.round(restoreTotal / 60.0 / restores);
        return new AvailabilityView(serviceId, name, current, target, availability, availability.compareTo(target) >= 0, outages,
                unavailable / 60, degraded / 60, mtrs);
    }

    @PreAuthorize("hasAuthority('operations:manage')")
    public List<MaintenanceView> maintenance() {
        return jdbc.query("""
                SELECT m.*, s.name AS service_name FROM maintenance_windows m JOIN catalog_services s ON s.id = m.service_id
                WHERE m.ends_at > ? ORDER BY m.starts_at
                """, (rs, i) -> new MaintenanceView(rs.getObject("id", UUID.class), rs.getObject("service_id", UUID.class), rs.getString("service_name"),
                rs.getString("title"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("impact"),
                rs.getBoolean("cancelled"), rs.getString("created_by")), Timestamp.from(Instant.now(clock).minus(Duration.ofDays(7))));
    }

    @Transactional
    @PreAuthorize("hasAuthority('operations:manage')")
    public MaintenanceView schedule(CreateMaintenanceRequest body) {
        requireService(body.serviceId());
        if (!body.endsAt().isAfter(body.startsAt())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW", "El fin debe ser posterior al inicio.");
        if (!body.startsAt().isAfter(Instant.now(clock))) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW", "Programa el mantenimiento a futuro.");
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO maintenance_windows (id, service_id, title, starts_at, ends_at, impact, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, body.serviceId(), body.title().trim(), Timestamp.from(body.startsAt()), Timestamp.from(body.endsAt()), body.impact(), actor(),
                Timestamp.from(Instant.now(clock)));
        audit.record(AuditAction.MAINTENANCE_SCHEDULED, "MAINTENANCE", id.toString(), body.title().trim() + "; impacto=" + body.impact());
        return maintenance().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    @PreAuthorize("hasAuthority('operations:manage')")
    public void cancelMaintenance(UUID id) {
        if (jdbc.update("UPDATE maintenance_windows SET cancelled = true WHERE id = ? AND NOT cancelled AND starts_at > ?", id, Timestamp.from(Instant.now(clock))) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MAINTENANCE_NOT_CANCELLABLE", "Solo se cancela un mantenimiento futuro que siga programado.");
        }
        audit.record(AuditAction.MAINTENANCE_CANCELLED, "MAINTENANCE", id.toString(), null);
    }

    private static final String EVENT_SELECT = """
            SELECT e.*, s.name AS service_name, t.code AS ticket_code FROM operational_events e
            LEFT JOIN catalog_services s ON s.id = e.service_id LEFT JOIN tickets t ON t.id = e.ticket_id
            """;

    private UUID insertEvent(UUID serviceId, String severity, String source, String title, String detail, String createdBy, Instant at, String status) {
        var id = UUID.randomUUID();
        var code = "EVT-%04d".formatted(jdbc.queryForObject("SELECT nextval('event_code_seq')", Long.class));
        jdbc.update("""
                INSERT INTO operational_events (id, code, service_id, severity, source, title, detail, status, created_by, created_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, code, serviceId, severity, source, title, detail, status, createdBy, Timestamp.from(at),
                "CLOSED".equals(status) ? Timestamp.from(at) : null);
        return id;
    }

    private EventView find(UUID id) {
        return jdbc.query(EVENT_SELECT + " WHERE e.id = ?", (rs, i) -> event(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Evento no encontrado."));
    }

    private static EventView event(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EventView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getObject("service_id", UUID.class), rs.getString("service_name"),
                rs.getString("severity"), rs.getString("source"), rs.getString("title"), rs.getString("detail"), rs.getString("status"),
                rs.getObject("ticket_id", UUID.class), rs.getString("ticket_code"), rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("acknowledged_by"), instant(rs.getTimestamp("acknowledged_at")), instant(rs.getTimestamp("closed_at")));
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private void requireService(UUID serviceId) {
        if (jdbc.queryForObject("SELECT count(*) FROM catalog_services WHERE id = ?", Long.class, serviceId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Servicio no encontrado.");
        }
    }

    private ApiException transition(UUID id) {
        find(id);
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Esta acción no corresponde al estado actual del evento.");
    }

    private static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
