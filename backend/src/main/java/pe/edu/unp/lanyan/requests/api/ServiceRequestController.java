package pe.edu.unp.lanyan.requests.api;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.requests.api.RequestDtos.*;
import pe.edu.unp.lanyan.requests.application.ServiceRequestService;
import pe.edu.unp.lanyan.shared.api.PageView;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {
    private final ServiceRequestService requests;
    public ServiceRequestController(ServiceRequestService requests) { this.requests = requests; }

    @PostMapping
    public ResponseEntity<ServiceRequestView> create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateServiceRequest body) {
        var request = requests.create(principal.getId(), body);
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + request.id())).body(request);
    }

    @GetMapping("/mine")
    public PageView<ServiceRequestView> mine(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return requests.mine(principal.getId(), page, size);
    }

    @GetMapping
    public PageView<ServiceRequestView> all(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return requests.all(page, size);
    }

    @PostMapping("/{id}/cancel")
    public ServiceRequestView cancel(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return requests.cancel(id, principal.getId());
    }

    @PostMapping("/{id}/decision")
    public ServiceRequestView decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return requests.decide(id, body.approved(), body.note(), principal.getId());
    }

    @PostMapping("/{id}/take")
    public ServiceRequestView take(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return requests.take(id, principal.getId());
    }

    @PostMapping("/{id}/fulfill")
    public ServiceRequestView fulfill(@PathVariable UUID id, @Valid @RequestBody FulfillRequest body) {
        return requests.fulfill(id, body.note());
    }
}
