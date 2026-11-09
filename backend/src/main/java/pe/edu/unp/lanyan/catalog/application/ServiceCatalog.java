package pe.edu.unp.lanyan.catalog.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.CategoryView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.CategoryWriteRequest;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.OperationalStatusRequest;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.ServiceView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.ServiceWriteRequest;
import pe.edu.unp.lanyan.catalog.domain.ItService;
import pe.edu.unp.lanyan.catalog.domain.ServiceCategory;
import pe.edu.unp.lanyan.catalog.domain.ServiceLifecycle;
import pe.edu.unp.lanyan.catalog.infrastructure.ItServiceRepository;
import pe.edu.unp.lanyan.catalog.infrastructure.ServiceCategoryRepository;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.shared.application.ApiException;

@Service
@Transactional(readOnly = true)
public class ServiceCatalog {
    private static final String TARGET = "CATALOG_SERVICE";

    private final ItServiceRepository services;
    private final ServiceCategoryRepository categories;
    private final AuditService audit;

    public ServiceCatalog(ItServiceRepository services, ServiceCategoryRepository categories, AuditService audit) {
        this.services = services;
        this.categories = categories;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('portal:it:access')")
    public PageView<ServiceView> listPublished(int page, int size) {
        return PageView.of(services.findByLifecycleStatus(ServiceLifecycle.PUBLISHED, pagination(page, size)).map(ServiceView::of));
    }

    @PreAuthorize("hasAuthority('portal:it:access')")
    public ServiceView getPublished(UUID id) {
        return services.findById(id).filter(service -> service.getLifecycleStatus() == ServiceLifecycle.PUBLISHED)
                .map(ServiceView::of).orElseThrow(ServiceCatalog::notFound);
    }

    @PreAuthorize("hasAuthority('portal:it:access')")
    public List<CategoryView> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryView::of).toList();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog:manage')")
    public PageView<ServiceView> listAll(int page, int size) {
        return PageView.of(services.findAll(pagination(page, size)).map(ServiceView::of));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog:manage')")
    public CategoryView createCategory(CategoryWriteRequest request) {
        if (categories.existsByNameIgnoreCase(request.name().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_EXISTS", "Ya existe una categoría con ese nombre.");
        }
        var category = categories.saveAndFlush(new ServiceCategory(request.name()));
        audit.record(AuditAction.CATALOG_CATEGORY_CREATED, "CATALOG_CATEGORY", category.getId().toString(), category.getName());
        return CategoryView.of(category);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog:manage')")
    public ServiceView create(ServiceWriteRequest request) {
        if (services.existsByNameIgnoreCase(request.name().trim())) throw duplicateName();
        var code = "SRV-%03d".formatted(services.nextCodeNumber());
        var service = services.saveAndFlush(new ItService(code, request.name(), request.description(), category(request.categoryId()),
                request.owner(), request.icon(), request.lifecycleStatus(), request.availabilityTarget(), request.responseTargetMinutes()));
        audit.record(AuditAction.CATALOG_SERVICE_CREATED, TARGET, service.getId().toString(), code + " " + service.getName());
        return ServiceView.of(service);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog:manage')")
    public ServiceView update(UUID id, ServiceWriteRequest request) {
        var service = services.findById(id).orElseThrow(ServiceCatalog::notFound);
        requireVersion(service, request.version());
        if (services.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) throw duplicateName();
        var previous = service.getLifecycleStatus();
        service.revise(request.name(), request.description(), category(request.categoryId()), request.owner(), request.icon(),
                request.lifecycleStatus(), request.availabilityTarget(), request.responseTargetMinutes());
        var saved = services.saveAndFlush(service);
        audit.record(AuditAction.CATALOG_SERVICE_UPDATED, TARGET, id.toString(),
                saved.getCode() + " estado " + previous + " a " + saved.getLifecycleStatus());
        return ServiceView.of(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('catalog:availability:update')")
    public ServiceView changeOperationalStatus(UUID id, OperationalStatusRequest request) {
        var service = services.findById(id).orElseThrow(ServiceCatalog::notFound);
        requireVersion(service, request.version());
        if (service.getLifecycleStatus() == ServiceLifecycle.RETIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "SERVICE_RETIRED", "El servicio está retirado.");
        }
        var previous = service.getOperationalStatus();
        service.changeOperationalStatus(request.status());
        var saved = services.saveAndFlush(service);
        audit.record(AuditAction.CATALOG_SERVICE_STATUS_CHANGED, TARGET, id.toString(),
                saved.getCode() + " " + previous + " a " + saved.getOperationalStatus());
        return ServiceView.of(saved);
    }

    private ServiceCategory category(UUID id) {
        return categories.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoría no encontrada."));
    }

    private static PageRequest pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        }
        return PageRequest.of(page, size, Sort.by("name").and(Sort.by("id")));
    }

    private static void requireVersion(ItService service, Long expected) {
        if (expected == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Envía la versión actual del servicio.");
        if (!expected.equals(service.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_RESOURCE", "El servicio cambió; vuelve a consultarlo.");
        }
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Servicio no encontrado."); }
    private static ApiException duplicateName() { return new ApiException(HttpStatus.CONFLICT, "SERVICE_NAME_EXISTS", "Ya existe un servicio con ese nombre."); }
}
