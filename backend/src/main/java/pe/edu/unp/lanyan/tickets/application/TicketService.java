package pe.edu.unp.lanyan.tickets.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.application.BusinessHours;
import pe.edu.unp.lanyan.catalog.application.SlaPolicies;
import pe.edu.unp.lanyan.catalog.domain.ServiceLifecycle;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.catalog.infrastructure.ItServiceRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.shared.application.ApiException;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.CreateTicketRequest;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.ServiceRef;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.SlaState;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.TicketView;
import pe.edu.unp.lanyan.tickets.domain.Ticket;
import pe.edu.unp.lanyan.tickets.domain.TicketStatus;
import pe.edu.unp.lanyan.tickets.infrastructure.TicketRepository;
import pe.edu.unp.lanyan.tickets.infrastructure.TicketCommentRepository;
import pe.edu.unp.lanyan.tickets.domain.TicketComment;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.CommentView;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gestión de incidentes (ITIL: Operación del servicio; ISO/IEC 20000-1, 8.6.1).
 * El solicitante no elige la prioridad: la fija el servicio. Los plazos se calculan al registrar
 * el ticket y solo cuentan el horario de atención del Instituto.
 */
@Service
@Transactional(readOnly = true)
public class TicketService {
    private final TicketRepository tickets;
    private final ItServiceRepository services;
    private final UserAccountRepository users;
    private final SlaPolicies policies;
    private final BusinessHours hours;
    private final AuditService audit;
    private final TicketCommentRepository comments;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public TicketService(TicketRepository tickets, ItServiceRepository services, UserAccountRepository users,
            SlaPolicies policies, BusinessHours hours, AuditService audit, TicketCommentRepository comments, JdbcTemplate jdbc) {
        this(tickets, services, users, policies, hours, audit, comments, jdbc, Clock.systemUTC());
    }

    TicketService(TicketRepository tickets, ItServiceRepository services, UserAccountRepository users,
            SlaPolicies policies, BusinessHours hours, AuditService audit, TicketCommentRepository comments, JdbcTemplate jdbc, Clock clock) {
        this.tickets = tickets; this.services = services; this.users = users;
        this.policies = policies; this.hours = hours; this.audit = audit; this.comments = comments; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('tickets:create') and #userId == authentication.principal.id")
    public TicketView create(UUID userId, CreateTicketRequest request) {
        var service = services.findById(request.serviceId())
                .filter(s -> s.getLifecycleStatus() == ServiceLifecycle.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Elige un servicio publicado del catálogo."));
        var requester = users.findById(userId).orElseThrow();
        var now = Instant.now(clock);
        var priority = service.getDefaultPriority();
        var policy = policies.forPriority(priority);
        var code = "INC-%04d".formatted(tickets.nextCodeNumber());
        var ticket = tickets.saveAndFlush(new Ticket(code, request.title(), request.description(), service, requester, priority, now,
                hours.plusBusinessMinutes(now, policy.getFirstResponseMinutes()),
                hours.plusBusinessMinutes(now, policy.getResolutionMinutes())));
        audit.record(AuditAction.TICKET_CREATED, "TICKET", ticket.getId().toString(),
                code + "; servicio=" + service.getName() + "; prioridad=" + priority);
        return view(ticket);
    }

    @PreAuthorize("hasAuthority('tickets:read:self') and #userId == authentication.principal.id")
    public PageView<TicketView> mine(UUID userId, int page, int size) {
        return withProblems(tickets.findByRequester_Id(userId, pagination(page, size)));
    }

    @PreAuthorize("hasAuthority('tickets:manage')")
    public PageView<TicketView> all(int page, int size) {
        return withProblems(tickets.findAll(pagination(page, size)));
    }

    /** El solicitante ve su ticket; un ticket ajeno responde como inexistente. */
    @PreAuthorize("isAuthenticated()")
    public TicketView get(UUID id, UUID userId, boolean manager) {
        var ticket = tickets.findById(id)
                .filter(t -> manager || (t.getRequester() != null && t.getRequester().getId().equals(userId)))
                .orElseThrow(TicketService::notFound);
        return view(ticket);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tickets:manage') and #actorId == authentication.principal.id")
    public TicketView take(UUID id, UUID actorId) {
        var ticket = locked(id);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_RESOLVED", "El ticket ya está resuelto.");
        }
        var actor = users.findById(actorId).orElseThrow();
        ticket.assignTo(actor, Instant.now(clock));
        var saved = tickets.saveAndFlush(ticket);
        audit.record(AuditAction.TICKET_ASSIGNED, "TICKET", id.toString(), saved.getCode() + "; técnico=" + actor.getEmail());
        return view(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tickets:manage') and #actorId == authentication.principal.id")
    public TicketView changeStatus(UUID id, TicketStatus status, UUID actorId) {
        var ticket = locked(id);
        if (status == TicketStatus.NEW) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Un ticket atendido no vuelve a Nuevo.");
        }
        var previous = ticket.getStatus();
        var now = Instant.now(clock);
        // Quien mueve un ticket sin responsable queda a cargo.
        if (ticket.getAssignee() == null) ticket.assignTo(users.findById(actorId).orElseThrow(), now);
        ticket.changeStatus(status, now);
        var saved = tickets.saveAndFlush(ticket);
        audit.record(AuditAction.TICKET_STATUS_CHANGED, "TICKET", id.toString(), saved.getCode() + "; " + previous + " a " + status);
        return view(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('tickets:manage') and #actorId == authentication.principal.id")
    public TicketView changePriority(UUID id, ServicePriority priority, String reason, UUID actorId) {
        var ticket = locked(id);
        var previous = ticket.getPriority();
        if (previous == priority) return view(ticket);
        var policy = policies.forPriority(priority);
        ticket.reprioritize(priority, hours.plusBusinessMinutes(ticket.getCreatedAt(), policy.getFirstResponseMinutes()),
                hours.plusBusinessMinutes(ticket.getCreatedAt(), policy.getResolutionMinutes()), Instant.now(clock));
        var saved = tickets.saveAndFlush(ticket);
        audit.record(AuditAction.TICKET_PRIORITY_CHANGED, "TICKET", id.toString(),
                saved.getCode() + "; " + previous + " a " + priority + "; motivo=" + reason.trim());
        return view(saved);
    }

    /** Comentarios visibles: el solicitante no ve las notas internas de TI. */
    @PreAuthorize("isAuthenticated()")
    public List<CommentView> comments(UUID id, UUID userId, boolean manager) {
        visible(id, userId, manager);
        var list = manager ? comments.findByTicket_IdOrderByCreatedAt(id) : comments.findByTicket_IdAndInternalFalseOrderByCreatedAt(id);
        return list.stream().map(TicketService::commentView).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('tickets:comment') and #userId == authentication.principal.id")
    public CommentView comment(UUID id, UUID userId, boolean manager, String body, boolean internal) {
        visible(id, userId, manager);
        if (internal && !manager) throw new ApiException(HttpStatus.FORBIDDEN, "INTERNAL_NOTE_FORBIDDEN", "Solo el equipo de TI escribe notas internas.");
        var ticket = locked(id);
        var author = users.findById(userId).orElseThrow();
        var now = Instant.now(clock);
        // Responderle al solicitante cuenta como primera respuesta; una nota interna no.
        if (manager && !internal) ticket.markFirstResponse(now);
        TicketComment saved;
        try {
            saved = comments.saveAndFlush(new TicketComment(ticket, author, author.getFullName(), manager, internal, body, now));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT", ex.getMessage());
        }
        tickets.saveAndFlush(ticket);
        audit.record(AuditAction.TICKET_COMMENTED, "TICKET", id.toString(), ticket.getCode() + (internal ? "; nota interna" : "; comentario"));
        return commentView(saved);
    }

    /** Lo usa la gestión de problemas al aplicar la solución definitiva: resuelve los incidentes vinculados abiertos. */
    @Transactional
    @PreAuthorize("hasAuthority('problems:manage') and #actorId == authentication.principal.id")
    public int resolveByProblem(Collection<UUID> ticketIds, String problemCode, String solution, UUID actorId) {
        var actor = users.findById(actorId).orElseThrow();
        int resolved = 0;
        for (var id : ticketIds) {
            var ticket = locked(id);
            if (ticket.getStatus() == TicketStatus.RESOLVED) continue;
            var now = Instant.now(clock);
            var previous = ticket.getStatus();
            if (ticket.getAssignee() == null) ticket.assignTo(actor, now);
            ticket.changeStatus(TicketStatus.RESOLVED, now);
            tickets.saveAndFlush(ticket);
            comments.saveAndFlush(new TicketComment(ticket, actor, actor.getFullName(), true, false,
                    "Resuelto con la solución definitiva del problema " + problemCode + ": " + solution.trim(), now));
            audit.record(AuditAction.TICKET_STATUS_CHANGED, "TICKET", id.toString(), ticket.getCode() + "; " + previous + " a RESOLVED; problema=" + problemCode);
            resolved++;
        }
        return resolved;
    }

    private void visible(UUID id, UUID userId, boolean manager) {
        tickets.findById(id).filter(t -> manager || (t.getRequester() != null && t.getRequester().getId().equals(userId)))
                .orElseThrow(TicketService::notFound);
    }

    private static CommentView commentView(TicketComment c) {
        return new CommentView(c.getId(), c.getAuthorName(), c.isStaff(), c.isInternal(), c.getBody(), c.getCreatedAt());
    }

    private PageView<TicketView> withProblems(org.springframework.data.domain.Page<Ticket> page) {
        var codes = problemCodes(page.getContent().stream().map(Ticket::getId).toList());
        return PageView.of(page.map(t -> view(t, codes.get(t.getId()))));
    }

    private Map<UUID, String> problemCodes(List<UUID> ticketIds) {
        var result = new HashMap<UUID, String>();
        if (ticketIds.isEmpty()) return result;
        var marks = String.join(",", java.util.Collections.nCopies(ticketIds.size(), "?"));
        jdbc.query("SELECT pi.ticket_id, p.code FROM problem_incidents pi JOIN problems p ON p.id = pi.problem_id WHERE pi.ticket_id IN (" + marks + ")",
                rs -> { result.put(rs.getObject(1, UUID.class), rs.getString(2)); }, ticketIds.toArray());
        return result;
    }

    private Ticket locked(UUID id) { return tickets.findForUpdate(id).orElseThrow(TicketService::notFound); }

    private TicketView view(Ticket t) { return view(t, problemCodes(List.of(t.getId())).get(t.getId())); }

    private TicketView view(Ticket t, String problemCode) {
        var now = Instant.now(clock);
        var service = t.getService();
        var resolutionEnd = t.getResolvedAt() == null ? now : t.getResolvedAt();
        long budget = hours.businessSecondsBetween(t.getCreatedAt(), t.getResolutionDue());
        long used = hours.businessSecondsBetween(t.getCreatedAt(), resolutionEnd);
        int percent = budget == 0 ? 100 : (int) Math.min(999, used * 100 / budget);
        return new TicketView(t.getId(), t.getCode(), t.getTitle(), t.getDescription(),
                new ServiceRef(service.getId(), service.getCode(), service.getName()),
                t.getRequesterName(), t.getRequesterEmail(), t.getAssigneeName(), t.getPriority(), t.getStatus(),
                t.getCreatedAt(), t.getFirstResponseDue(), t.getResolutionDue(), t.getFirstRespondedAt(), t.getResolvedAt(),
                state(t.getCreatedAt(), t.getFirstResponseDue(), t.getFirstRespondedAt(), now),
                state(t.getCreatedAt(), t.getResolutionDue(), t.getResolvedAt(), now), percent, t.getVersion(), problemCode);
    }

    private SlaState state(Instant start, Instant due, Instant done, Instant now) {
        if (done != null) return done.isAfter(due) ? SlaState.MISSED : SlaState.MET;
        if (now.isAfter(due)) return SlaState.BREACHED;
        long budget = hours.businessSecondsBetween(start, due);
        long used = hours.businessSecondsBetween(start, now);
        return budget > 0 && used * 4 >= budget * 3 ? SlaState.AT_RISK : SlaState.ON_TIME;
    }

    private static PageRequest pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        }
        return PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket no encontrado."); }
}
