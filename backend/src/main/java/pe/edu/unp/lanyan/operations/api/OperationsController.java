package pe.edu.unp.lanyan.operations.api;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.operations.api.OperationsDtos.*;
import pe.edu.unp.lanyan.operations.application.OperationsService;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService operations;
    public OperationsController(OperationsService operations) { this.operations = operations; }

    @GetMapping("/events")
    public List<EventView> events() { return operations.events(); }

    @PostMapping("/events")
    public ResponseEntity<EventView> register(@Valid @RequestBody CreateEventRequest body) {
        return ResponseEntity.status(201).body(operations.register(body));
    }

    @PostMapping("/events/{id}/acknowledge")
    public EventView acknowledge(@PathVariable UUID id) { return operations.acknowledge(id); }

    @PostMapping("/events/{id}/close")
    public EventView close(@PathVariable UUID id) { return operations.close(id); }

    @PostMapping("/events/{id}/incident")
    public EventView incident(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return operations.createIncident(id, principal.getId());
    }

    @GetMapping("/availability")
    public List<AvailabilityView> availability() { return operations.availability(); }

    @GetMapping("/maintenance")
    public List<MaintenanceView> maintenance() { return operations.maintenance(); }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceView> schedule(@Valid @RequestBody CreateMaintenanceRequest body) {
        return ResponseEntity.status(201).body(operations.schedule(body));
    }

    @PostMapping("/maintenance/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        operations.cancelMaintenance(id);
        return ResponseEntity.noContent().build();
    }
}
