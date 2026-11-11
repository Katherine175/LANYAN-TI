package pe.edu.unp.lanyan.requests.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.domain.ServiceLifecycle;
import pe.edu.unp.lanyan.catalog.infrastructure.ItServiceRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import pe.edu.unp.lanyan.requests.api.RequestDtos.CreateServiceRequest;
import pe.edu.unp.lanyan.requests.api.RequestDtos.ServiceRequestView;
import pe.edu.unp.lanyan.requests.domain.ServiceRequest;
import pe.edu.unp.lanyan.requests.infrastructure.ServiceRequestRepository;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.shared.application.ApiException;

/** Cumplimiento de solicitudes (ITIL: Operación del servicio; ISO/IEC 20000-1, 8.6.2). */
@Service
@Transactional(readOnly = true)
public class ServiceRequestService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final EnumSet<ServiceRequest.Status> OPEN =
            EnumSet.of(ServiceRequest.Status.PENDING, ServiceRequest.Status.APPROVED, ServiceRequest.Status.IN_PROGRESS);

    private final ServiceRequestRepository requests;
    private final ItServiceRepository services;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public ServiceRequestService(ServiceRequestRepository requests, ItServiceRepository services, UserAccountRepository users, AuditService audit) {
        this.requests = requests; this.services = services; this.users = users; this.audit = audit;
    }

    @Transactional
    @PreAuthorize("hasAuthority('requests:create') and #userId == authentication.principal.id")
    public ServiceRequestView create(UUID userId, CreateServiceRequest body) {
        var service = services.findById(body.serviceId()).filter(s -> s.getLifecycleStatus() == ServiceLifecycle.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Elige un servicio publicado del catálogo."));
        if (body.neededBy() != null && body.neededBy().isBefore(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "La fecha requerida no puede estar en el pasado.");
        }
        var code = "SOL-%04d".formatted(requests.nextCodeNumber());
        var request = requests.saveAndFlush(new ServiceRequest(code, service, body.title(), body.justification(), body.neededBy(),
                users.findById(userId).orElseThrow(), Instant.now(clock)));
        audit.record(AuditAction.SERVICE_REQUEST_CREATED, "SERVICE_REQUEST", request.getId().toString(), code + "; servicio=" + service.getName());
        return view(request);
    }

    @PreAuthorize("hasAuthority('requests:create') and #userId == authentication.principal.id")
    public PageView<ServiceRequestView> mine(UUID userId, int page, int size) {
        return PageView.of(requests.findByRequester_Id(userId, pagination(page, size)).map(this::view));
    }

    @PreAuthorize("hasAuthority('requests:fulfill') or hasAuthority('requests:approve')")
    public PageView<ServiceRequestView> all(int page, int size) {
        return PageView.of(requests.findAll(pagination(page, size)).map(this::view));
    }

    @Transactional
    @PreAuthorize("hasAuthority('requests:create') and #userId == authentication.principal.id")
    public ServiceRequestView cancel(UUID id, UUID userId) {
        return change(id, r -> {
            if (r.getRequester() == null || !r.getRequester().getId().equals(userId)) throw notFound();
            r.cancel(Instant.now(clock));
        }, AuditAction.SERVICE_REQUEST_CANCELLED, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('requests:approve') and #actorId == authentication.principal.id")
    public ServiceRequestView decide(UUID id, boolean approved, String note, UUID actorId) {
        if (!approved && (note == null || note.trim().length() < 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Indica el motivo del rechazo (mínimo 5 caracteres).");
        }
        var approver = users.findById(actorId).orElseThrow();
        return change(id, r -> r.decide(approved, approver, note, Instant.now(clock)),
                approved ? AuditAction.SERVICE_REQUEST_APPROVED : AuditAction.SERVICE_REQUEST_REJECTED, note);
    }

    @Transactional
    @PreAuthorize("hasAuthority('requests:fulfill') and #actorId == authentication.principal.id")
    public ServiceRequestView take(UUID id, UUID actorId) {
        var technician = users.findById(actorId).orElseThrow();
        return change(id, r -> r.assignTo(technician, Instant.now(clock)), AuditAction.SERVICE_REQUEST_ASSIGNED, technician.getEmail());
    }

    @Transactional
    @PreAuthorize("hasAuthority('requests:fulfill')")
    public ServiceRequestView fulfill(UUID id, String note) {
        return change(id, r -> r.fulfill(note, Instant.now(clock)), AuditAction.SERVICE_REQUEST_FULFILLED, note);
    }

    private ServiceRequestView change(UUID id, Consumer<ServiceRequest> action, AuditAction event, String detail) {
        var request = requests.findForUpdate(id).orElseThrow(ServiceRequestService::notFound);
        try {
            action.accept(request);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Esta acción no corresponde al estado actual de la solicitud.");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_DATA", ex.getMessage());
        }
        var saved = requests.saveAndFlush(request);
        audit.record(event, "SERVICE_REQUEST", id.toString(), saved.getCode() + (detail == null || detail.isBlank() ? "" : "; " + detail.trim()));
        return view(saved);
    }

    private ServiceRequestView view(ServiceRequest r) {
        boolean overdue = r.getNeededBy() != null && OPEN.contains(r.getStatus()) && r.getNeededBy().isBefore(today());
        return new ServiceRequestView(r.getId(), r.getCode(), r.getService().getId(), r.getService().getName(), r.getTitle(),
                r.getJustification(), r.getNeededBy(), overdue, r.getRequesterName(), r.getRequesterEmail(), r.getStatus(),
                r.getDecidedByName(), r.getDecidedAt(), r.getDecisionNote(), r.getAssigneeName(), r.getFulfilledAt(),
                r.getFulfillmentNote(), r.getCreatedAt());
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(LIMA)); }

    private static PageRequest pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        return PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Solicitud no encontrada."); }
}
