package pe.edu.unp.lanyan.catalog.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.CategoryView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.CategoryWriteRequest;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.OperationalStatusRequest;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.ServiceView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.ServiceWriteRequest;
import pe.edu.unp.lanyan.catalog.application.ServiceCatalog;
import pe.edu.unp.lanyan.shared.api.PageView;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final ServiceCatalog catalog;

    public CatalogController(ServiceCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/services")
    public PageView<ServiceView> published(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return catalog.listPublished(page, size);
    }

    @GetMapping("/services/{id}")
    public ServiceView published(@PathVariable UUID id) { return catalog.getPublished(id); }

    @GetMapping("/categories")
    public List<CategoryView> categories() { return catalog.listCategories(); }

    @PutMapping("/services/{id}/operational-status")
    public ServiceView operationalStatus(@PathVariable UUID id, @Valid @RequestBody OperationalStatusRequest request) {
        return catalog.changeOperationalStatus(id, request);
    }

    @GetMapping("/admin/services")
    public PageView<ServiceView> all(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return catalog.listAll(page, size);
    }

    @PostMapping("/admin/services")
    public ResponseEntity<ServiceView> create(@Valid @RequestBody ServiceWriteRequest request) {
        var service = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/services/" + service.id())).body(service);
    }

    @PutMapping("/admin/services/{id}")
    public ServiceView update(@PathVariable UUID id, @Valid @RequestBody ServiceWriteRequest request) {
        return catalog.update(id, request);
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryWriteRequest request) {
        var category = catalog.createCategory(request);
        return ResponseEntity.status(201).body(category);
    }
}
