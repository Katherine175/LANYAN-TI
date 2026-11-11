package pe.edu.unp.lanyan.catalog.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.SlaPolicyView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.SlaPolicyWriteRequest;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;
import pe.edu.unp.lanyan.catalog.domain.SlaPolicy;
import pe.edu.unp.lanyan.catalog.infrastructure.ItServiceRepository;
import pe.edu.unp.lanyan.catalog.infrastructure.SlaPolicyRepository;
import pe.edu.unp.lanyan.shared.application.ApiException;

/** Acuerdos de nivel de servicio por prioridad (ISO/IEC 20000-1, 8.3.3). */
@Service
@Transactional(readOnly = true)
public class SlaPolicies {
    private final SlaPolicyRepository policies;
    private final ItServiceRepository services;
    private final AuditService audit;

    public SlaPolicies(SlaPolicyRepository policies, ItServiceRepository services, AuditService audit) {
        this.policies = policies;
        this.services = services;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('portal:it:access')")
    public List<SlaPolicyView> list() {
        return policies.findAll().stream().sorted(Comparator.comparing(SlaPolicy::getPriority)).map(SlaPolicyView::of).toList();
    }

    /** Para uso interno: la política vigente de una prioridad. */
    public SlaPolicy forPriority(ServicePriority priority) {
        return policies.findById(priority).orElseThrow(() -> new IllegalStateException("Falta la política SLA " + priority));
    }

    /** Los tickets ya registrados conservan los plazos con los que nacieron. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('sla:manage')")
    public SlaPolicyView update(ServicePriority priority, SlaPolicyWriteRequest request) {
        var policy = forPriority(priority);
        if (!request.version().equals(policy.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_RESOURCE", "Los tiempos cambiaron; vuelve a consultarlos.");
        }
        if (request.resolutionMinutes() < request.firstResponseMinutes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLA", "La resolución no puede ser menor que la primera respuesta.");
        }
        var before = policy.getFirstResponseMinutes() + "/" + policy.getResolutionMinutes();
        policy.revise(request.firstResponseMinutes(), request.resolutionMinutes());
        var saved = policies.saveAndFlush(policy);
        services.syncResponseTarget(priority.name(), saved.getFirstResponseMinutes());
        audit.record(AuditAction.SLA_POLICY_UPDATED, "SLA_POLICY", priority.name(),
                "respuesta/resolución en minutos " + before + " a " + saved.getFirstResponseMinutes() + "/" + saved.getResolutionMinutes());
        return SlaPolicyView.of(saved);
    }
}
