package pe.edu.unp.lanyan.problems.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.infrastructure.ItServiceRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import pe.edu.unp.lanyan.problems.api.ProblemDtos.*;
import pe.edu.unp.lanyan.problems.domain.Problem;
import pe.edu.unp.lanyan.problems.infrastructure.ProblemRepository;
import pe.edu.unp.lanyan.shared.application.ApiException;
import pe.edu.unp.lanyan.tickets.application.TicketService;
import pe.edu.unp.lanyan.tickets.domain.TicketStatus;

/**
 * Gestión de problemas (ITIL: Operación del servicio; ISO/IEC 20000-1, 8.6.3): control de problemas,
 * control de errores conocidos y detección proactiva de incidentes repetidos.
 */
@Service
@Transactional(readOnly = true)
public class ProblemService {
    /** Incidentes del mismo servicio en 30 días a partir de los cuales conviene abrir un problema. */
    static final int CANDIDATE_THRESHOLD = 3;

    private final ProblemRepository problems;
    private final ItServiceRepository services;
    private final UserAccountRepository users;
    private final TicketService tickets;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public ProblemService(ProblemRepository problems, ItServiceRepository services, UserAccountRepository users,
            TicketService tickets, JdbcTemplate jdbc, AuditService audit) {
        this.problems = problems; this.services = services; this.users = users; this.tickets = tickets; this.jdbc = jdbc; this.audit = audit;
    }

    @PreAuthorize("hasAuthority('problems:manage')")
    public List<ProblemView> list() {
        return problems.findAll(Sort.by("createdAt").descending()).stream().map(this::view).toList();
    }

    @PreAuthorize("hasAuthority('problems:manage')")
    public ProblemView get(UUID id) { return view(problems.findById(id).orElseThrow(ProblemService::notFound)); }

    @Transactional
    @PreAuthorize("hasAuthority('problems:manage') and #actorId == authentication.principal.id")
    public ProblemView create(CreateProblemRequest body, UUID actorId) {
        var service = services.findById(body.serviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Servicio no encontrado."));
        var owner = users.findById(actorId).orElseThrow();
        var code = "PRB-%04d".formatted(problems.nextCodeNumber());
        var problem = guard(() -> problems.saveAndFlush(new Problem(code, body.title(), body.description(), service, body.priority(), owner, Instant.now(clock))));
        audit.record(AuditAction.PROBLEM_CREATED, "PROBLEM", problem.getId().toString(), code + "; servicio=" + service.getName());
        if (body.ticketIds() != null) body.ticketIds().forEach(ticketId -> insertLink(problem, ticketId, owner.getFullName()));
        return view(problem);
    }

    @Transactional
    @PreAuthorize("hasAuthority('problems:manage') and #actorId == authentication.principal.id")
    public ProblemView link(UUID id, UUID ticketId, UUID actorId) {
        var problem = problems.findForUpdate(id).orElseThrow(ProblemService::notFound);
        if (problem.getStatus() == Problem.Status.RESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "PROBLEM_RESOLVED", "El problema ya está resuelto.");
        }
        insertLink(problem, ticketId, users.findById(actorId).orElseThrow().getFullName());
        return view(problem);
    }

    @Transactional
    @PreAuthorize("hasAuthority('problems:manage')")
    public ProblemView unlink(UUID id, UUID ticketId) {
        var problem = problems.findForUpdate(id).orElseThrow(ProblemService::notFound);
        if (jdbc.update("DELETE FROM problem_incidents WHERE problem_id = ? AND ticket_id = ?", id, ticketId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Ese incidente no está vinculado a este problema.");
        }
        audit.record(AuditAction.PROBLEM_UNLINKED, "PROBLEM", id.toString(), problem.getCode() + "; ticket=" + ticketCode(ticketId));
        return view(problem);
    }

    @Transactional
    @PreAuthorize("hasAuthority('problems:manage')")
    public ProblemView documentKnownError(UUID id, KnownErrorRequest body) {
        var problem = problems.findForUpdate(id).orElseThrow(ProblemService::notFound);
        guard(() -> { problem.documentKnownError(body.rootCause(), body.workaround(), Instant.now(clock)); return problem; });
        problems.saveAndFlush(problem);
        audit.record(AuditAction.PROBLEM_KNOWN_ERROR, "PROBLEM", id.toString(), problem.getCode() + "; causa=" + abbreviate(body.rootCause()));
        return view(problem);
    }

    @Transactional
    @PreAuthorize("hasAuthority('problems:manage') and #actorId == authentication.principal.id")
    public ProblemView resolve(UUID id, ResolveRequest body, UUID actorId) {
        var problem = problems.findForUpdate(id).orElseThrow(ProblemService::notFound);
        guard(() -> { problem.resolve(body.permanentSolution(), Instant.now(clock)); return problem; });
        problems.saveAndFlush(problem);
        int closed = 0;
        if (body.closeLinked()) {
            var open = linkedIncidents(id).stream().filter(i -> i.status() != TicketStatus.RESOLVED).map(LinkedIncident::id).toList();
            closed = tickets.resolveByProblem(open, problem.getCode(), body.permanentSolution(), actorId);
        }
        audit.record(AuditAction.PROBLEM_RESOLVED, "PROBLEM", id.toString(), problem.getCode() + "; incidentes resueltos=" + closed);
        return view(problem);
    }

    /** Detección proactiva: servicios con incidentes repetidos en 30 días que no están vinculados a un problema. */
    @PreAuthorize("hasAuthority('problems:manage')")
    public List<Candidate> candidates() {
        return jdbc.query("""
                SELECT s.id, s.name, count(*) AS incidents, max(t.created_at) AS last_at
                FROM tickets t JOIN catalog_services s ON s.id = t.service_id
                WHERE t.created_at > ? AND NOT EXISTS (SELECT 1 FROM problem_incidents pi WHERE pi.ticket_id = t.id)
                GROUP BY s.id, s.name HAVING count(*) >= ? ORDER BY incidents DESC, s.name
                """, (rs, i) -> new Candidate(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("incidents"),
                rs.getTimestamp("last_at").toInstant()),
                Timestamp.from(Instant.now(clock).minusSeconds(30L * 24 * 3600)), CANDIDATE_THRESHOLD);
    }

    /** Errores conocidos del servicio, para que el técnico aplique la solución temporal al atender un ticket. */
    @PreAuthorize("hasAuthority('tickets:manage')")
    public List<KnownError> knownErrors(UUID serviceId) {
        return problems.findByService_IdAndStatusIn(serviceId, List.of(Problem.Status.KNOWN_ERROR, Problem.Status.RESOLVED)).stream()
                .map(p -> new KnownError(p.getId(), p.getCode(), p.getTitle(), p.getRootCause(), p.getWorkaround(), p.getStatus())).toList();
    }

    private void insertLink(Problem problem, UUID ticketId, String actorName) {
        var serviceId = jdbc.query("SELECT service_id FROM tickets WHERE id = ?", (rs, i) -> rs.getObject(1, UUID.class), ticketId)
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket no encontrado."));
        if (!serviceId.equals(problem.getService().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_MISMATCH", "El incidente es de otro servicio.");
        }
        var current = jdbc.queryForList("SELECT p.code FROM problem_incidents pi JOIN problems p ON p.id = pi.problem_id WHERE pi.ticket_id = ?", String.class, ticketId);
        if (!current.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_ALREADY_LINKED", "El incidente ya está vinculado a " + current.get(0) + ".");
        }
        jdbc.update("INSERT INTO problem_incidents (problem_id, ticket_id, linked_at, linked_by_name) VALUES (?, ?, ?, ?)",
                problem.getId(), ticketId, Timestamp.from(Instant.now(clock)), actorName);
        audit.record(AuditAction.PROBLEM_LINKED, "PROBLEM", problem.getId().toString(), problem.getCode() + "; ticket=" + ticketCode(ticketId));
    }

    private List<LinkedIncident> linkedIncidents(UUID problemId) {
        return jdbc.query("""
                SELECT t.id, t.code, t.title, t.status, t.created_at FROM problem_incidents pi JOIN tickets t ON t.id = pi.ticket_id
                WHERE pi.problem_id = ? ORDER BY t.created_at DESC
                """, (rs, i) -> new LinkedIncident(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("title"),
                TicketStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant()), problemId);
    }

    private String ticketCode(UUID ticketId) {
        return jdbc.queryForList("SELECT code FROM tickets WHERE id = ?", String.class, ticketId).stream().findFirst().orElse(ticketId.toString());
    }

    private ProblemView view(Problem p) {
        return new ProblemView(p.getId(), p.getCode(), p.getTitle(), p.getDescription(), p.getService().getId(), p.getService().getName(),
                p.getPriority(), p.getStatus(), p.getOwnerName(), p.getRootCause(), p.getWorkaround(), p.getPermanentSolution(),
                p.getCreatedAt(), p.getKnownErrorAt(), p.getResolvedAt(), linkedIncidents(p.getId()));
    }

    private static <T> T guard(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_DATA", ex.getMessage());
        }
    }

    private static String abbreviate(String text) { return text.length() <= 200 ? text.trim() : text.trim().substring(0, 200) + "…"; }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "Problema no encontrado."); }
}
