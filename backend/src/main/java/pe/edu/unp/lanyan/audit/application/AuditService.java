package pe.edu.unp.lanyan.audit.application;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.unp.lanyan.audit.api.AuditEventView;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.audit.domain.AuditEvent;
import pe.edu.unp.lanyan.audit.infrastructure.AuditEventRepository;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.shared.application.ApiException;

@Service
public class AuditService {
    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) { this.events = events; }

    /** Se une a la transacción en curso: si la operación se revierte, su registro también. */
    @Transactional
    public void record(AuditAction action, String targetType, String targetId, String detail) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var actor = authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal ? principal : null;
        events.save(new AuditEvent(action, actor == null ? null : actor.getId(), actor == null ? null : actor.getUsername(),
                targetType, targetId, remoteAddress(), detail));
    }

    /** Para login y logout, donde la identidad no está (o ya no está) en el contexto de seguridad. */
    @Transactional
    public void recordAuthentication(AuditAction action, UUID actorId, String email) {
        events.save(new AuditEvent(action, actorId, email, null, null, remoteAddress(), null));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('audit:read')")
    public PageView<AuditEventView> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        }
        var request = PageRequest.of(page, size, Sort.by("occurredAt").descending().and(Sort.by("id")));
        return PageView.of(events.findAll(request).map(AuditEventView::of));
    }

    private static String remoteAddress() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
    }
}
