package pe.edu.unp.lanyan.access.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.access.api.AccessDtos.AccessRequestView;
import pe.edu.unp.lanyan.access.api.AccessDtos.CreateAccessRequest;
import pe.edu.unp.lanyan.access.api.AccessDtos.ResourceView;
import pe.edu.unp.lanyan.access.domain.AccessRequest;
import pe.edu.unp.lanyan.access.infrastructure.AccessRequestRepository;
import pe.edu.unp.lanyan.access.infrastructure.AccessResourceRepository;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.shared.application.ApiException;

/**
 * Gestión de accesos (ITIL: Operación del servicio; ISO/IEC 20000-1, 8.7.3): concede los derechos
 * aprobados por la Jefatura de TI, verifica la identidad del solicitante y permite revocarlos.
 */
@Service
@Transactional(readOnly = true)
public class AccessService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final AccessRequestRepository requests;
    private final AccessResourceRepository resources;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public AccessService(AccessRequestRepository requests, AccessResourceRepository resources, UserAccountRepository users, AuditService audit) {
        this.requests = requests; this.resources = resources; this.users = users; this.audit = audit;
    }

    @PreAuthorize("hasAuthority('access:request')")
    public List<ResourceView> resources() {
        return resources.findByActiveTrueOrderByName().stream()
                .map(r -> new ResourceView(r.getId(), r.getName(), r.getDescription(), r.getOwner())).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('access:request') and #userId == authentication.principal.id")
    public AccessRequestView request(UUID userId, CreateAccessRequest body) {
        var resource = resources.findById(body.resourceId()).filter(r -> r.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso no encontrado."));
        if (body.expiresOn() != null && !body.expiresOn().isAfter(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "La fecha de vencimiento debe ser posterior a hoy.");
        }
        var today = today();
        if (requests.findByRequester_IdAndResource_IdAndStatusIn(userId, resource.getId(),
                EnumSet.of(AccessRequest.Status.PENDING, AccessRequest.Status.APPROVED)).stream()
                .anyMatch(r -> r.getStatus() == AccessRequest.Status.PENDING || r.isActiveOn(today))) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCESS_ALREADY_REQUESTED",
                    "Ya tienes una solicitud pendiente o un acceso aprobado para este recurso.");
        }
        var code = "ACC-%04d".formatted(requests.nextCodeNumber());
        var request = requests.saveAndFlush(new AccessRequest(code, resource, body.level(), body.justification(), body.expiresOn(),
                users.findById(userId).orElseThrow(), Instant.now(clock)));
        audit.record(AuditAction.ACCESS_REQUESTED, "ACCESS_REQUEST", request.getId().toString(),
                code + "; recurso=" + resource.getName() + "; nivel=" + body.level());
        return view(request);
    }

    @PreAuthorize("hasAuthority('access:request') and #userId == authentication.principal.id")
    public PageView<AccessRequestView> mine(UUID userId, int page, int size) {
        return PageView.of(requests.findByRequester_Id(userId, pagination(page, size)).map(this::view));
    }

    @PreAuthorize("hasAuthority('access:approve') or hasAuthority('requests:fulfill')")
    public PageView<AccessRequestView> all(int page, int size) {
        return PageView.of(requests.findAll(pagination(page, size)).map(this::view));
    }

    @Transactional
    @PreAuthorize("hasAuthority('access:request') and #userId == authentication.principal.id")
    public AccessRequestView cancel(UUID id, UUID userId) {
        return change(id, r -> {
            if (r.getRequester() == null || !r.getRequester().getId().equals(userId)) throw notFound();
            r.cancel(Instant.now(clock));
        }, AuditAction.ACCESS_CANCELLED, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('access:approve') and #actorId == authentication.principal.id")
    public AccessRequestView decide(UUID id, boolean approved, String note, UUID actorId) {
        if (!approved && (note == null || note.trim().length() < 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Indica el motivo del rechazo (mínimo 5 caracteres).");
        }
        var approver = users.findById(actorId).orElseThrow();
        return change(id, r -> {
            // Verificación de identidad: no se concede acceso a una cuenta eliminada o desactivada.
            if (approved && (r.getRequester() == null || !r.getRequester().isActive())) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUESTER_INACTIVE", "La cuenta del solicitante no está activa.");
            }
            r.decide(approved, approver, note, Instant.now(clock));
        }, approved ? AuditAction.ACCESS_GRANTED : AuditAction.ACCESS_REJECTED, note);
    }

    @Transactional
    @PreAuthorize("hasAuthority('access:approve') and #actorId == authentication.principal.id")
    public AccessRequestView revoke(UUID id, String reason, UUID actorId) {
        var actor = users.findById(actorId).orElseThrow();
        return change(id, r -> r.revoke(actor, reason, Instant.now(clock)), AuditAction.ACCESS_REVOKED, reason);
    }

    private AccessRequestView change(UUID id, Consumer<AccessRequest> action, AuditAction event, String detail) {
        var request = requests.findForUpdate(id).orElseThrow(AccessService::notFound);
        try {
            action.accept(request);
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Esta acción no corresponde al estado actual de la solicitud.");
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_DATA", ex.getMessage());
        }
        var saved = requests.saveAndFlush(request);
        audit.record(event, "ACCESS_REQUEST", id.toString(), saved.getCode() + "; " + saved.getRequesterEmail() + " en " + saved.getResource().getName()
                + (detail == null || detail.isBlank() ? "" : "; " + detail.trim()));
        return view(saved);
    }

    private AccessRequestView view(AccessRequest r) {
        var today = today();
        boolean active = r.isActiveOn(today);
        boolean expired = r.getStatus() == AccessRequest.Status.APPROVED && !active;
        var resource = r.getResource();
        return new AccessRequestView(r.getId(), r.getCode(), resource.getId(), resource.getName(), resource.getOwner(), r.getLevel(),
                r.getJustification(), r.getExpiresOn(), r.getRequesterName(), r.getRequesterEmail(), r.getStatus(), active, expired,
                r.getDecidedByName(), r.getDecidedAt(), r.getDecisionNote(), r.getRevokedByName(), r.getRevokedAt(), r.getRevokeReason(),
                r.getCreatedAt());
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(LIMA)); }

    private static PageRequest pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        return PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "ACCESS_REQUEST_NOT_FOUND", "Solicitud de acceso no encontrada."); }
}
