package pe.edu.unp.lanyan.access.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.access.api.AccessDtos.*;
import pe.edu.unp.lanyan.access.application.AccessService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.shared.api.PageView;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {
    private final AccessService access;
    public AccessController(AccessService access) { this.access = access; }

    @GetMapping("/resources")
    public List<ResourceView> resources() { return access.resources(); }

    @PostMapping("/requests")
    public ResponseEntity<AccessRequestView> request(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateAccessRequest body) {
        var request = access.request(principal.getId(), body);
        return ResponseEntity.created(URI.create("/api/v1/access/requests/" + request.id())).body(request);
    }

    @GetMapping("/requests/mine")
    public PageView<AccessRequestView> mine(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return access.mine(principal.getId(), page, size);
    }

    @GetMapping("/requests")
    public PageView<AccessRequestView> all(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return access.all(page, size);
    }

    @PostMapping("/requests/{id}/cancel")
    public AccessRequestView cancel(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return access.cancel(id, principal.getId());
    }

    @PostMapping("/requests/{id}/decision")
    public AccessRequestView decide(@PathVariable UUID id, @Valid @RequestBody AccessDecisionRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return access.decide(id, body.approved(), body.note(), principal.getId());
    }

    @PostMapping("/requests/{id}/revoke")
    public AccessRequestView revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return access.revoke(id, body.reason(), principal.getId());
    }
}
