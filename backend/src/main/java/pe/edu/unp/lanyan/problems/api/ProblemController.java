package pe.edu.unp.lanyan.problems.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.problems.api.ProblemDtos.*;
import pe.edu.unp.lanyan.problems.application.ProblemService;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {
    private final ProblemService problems;
    public ProblemController(ProblemService problems) { this.problems = problems; }

    @GetMapping
    public List<ProblemView> list() { return problems.list(); }

    @GetMapping("/candidates")
    public List<Candidate> candidates() { return problems.candidates(); }

    @GetMapping("/known-errors")
    public List<KnownError> knownErrors(@RequestParam UUID serviceId) { return problems.knownErrors(serviceId); }

    @GetMapping("/{id}")
    public ProblemView get(@PathVariable UUID id) { return problems.get(id); }

    @PostMapping
    public ResponseEntity<ProblemView> create(@Valid @RequestBody CreateProblemRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        var problem = problems.create(body, principal.getId());
        return ResponseEntity.created(URI.create("/api/v1/problems/" + problem.id())).body(problem);
    }

    @PostMapping("/{id}/incidents")
    public ProblemView link(@PathVariable UUID id, @Valid @RequestBody LinkRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return problems.link(id, body.ticketId(), principal.getId());
    }

    @DeleteMapping("/{id}/incidents/{ticketId}")
    public ProblemView unlink(@PathVariable UUID id, @PathVariable UUID ticketId) { return problems.unlink(id, ticketId); }

    @PutMapping("/{id}/known-error")
    public ProblemView knownError(@PathVariable UUID id, @Valid @RequestBody KnownErrorRequest body) {
        return problems.documentKnownError(id, body);
    }

    @PostMapping("/{id}/resolve")
    public ProblemView resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest body, @AuthenticationPrincipal AccountPrincipal principal) {
        return problems.resolve(id, body, principal.getId());
    }
}
