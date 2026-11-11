package pe.edu.unp.lanyan.catalog.api;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.SlaPolicyView;
import pe.edu.unp.lanyan.catalog.api.CatalogDtos.SlaPolicyWriteRequest;
import pe.edu.unp.lanyan.catalog.application.SlaPolicies;
import pe.edu.unp.lanyan.catalog.domain.ServicePriority;

@RestController
@RequestMapping("/api/v1/sla/policies")
public class SlaController {
    private final SlaPolicies policies;
    public SlaController(SlaPolicies policies) { this.policies = policies; }

    @GetMapping
    public List<SlaPolicyView> list() { return policies.list(); }

    @PutMapping("/{priority}")
    public SlaPolicyView update(@PathVariable ServicePriority priority, @Valid @RequestBody SlaPolicyWriteRequest request) {
        return policies.update(priority, request);
    }
}
