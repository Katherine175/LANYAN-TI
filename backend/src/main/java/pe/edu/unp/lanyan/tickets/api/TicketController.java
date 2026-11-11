package pe.edu.unp.lanyan.tickets.api;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.tickets.api.TicketDtos.*;
import pe.edu.unp.lanyan.tickets.application.TicketService;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService tickets;
    public TicketController(TicketService tickets) { this.tickets = tickets; }

    @PostMapping
    public ResponseEntity<TicketView> create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateTicketRequest request) {
        var ticket = tickets.create(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.id())).body(ticket);
    }

    @GetMapping("/mine")
    public PageView<TicketView> mine(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return tickets.mine(principal.getId(), page, size);
    }

    @GetMapping
    public PageView<TicketView> all(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return tickets.all(page, size);
    }

    @GetMapping("/{id}")
    public TicketView get(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        boolean manager = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("tickets:manage"));
        return tickets.get(id, principal.getId(), manager);
    }

    @GetMapping("/{id}/comments")
    public java.util.List<CommentView> comments(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return tickets.comments(id, principal.getId(), manager(principal));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentView> comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.status(201).body(tickets.comment(id, principal.getId(), manager(principal), body.body(), body.isInternal()));
    }

    private static boolean manager(AccountPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("tickets:manage"));
    }

    @PostMapping("/{id}/take")
    public TicketView take(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return tickets.take(id, principal.getId());
    }

    @PutMapping("/{id}/status")
    public TicketView status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal AccountPrincipal principal) {
        return tickets.changeStatus(id, request.status(), principal.getId());
    }

    @PutMapping("/{id}/priority")
    public TicketView priority(@PathVariable UUID id, @Valid @RequestBody PriorityRequest request, @AuthenticationPrincipal AccountPrincipal principal) {
        return tickets.changePriority(id, request.priority(), request.reason(), principal.getId());
    }
}
