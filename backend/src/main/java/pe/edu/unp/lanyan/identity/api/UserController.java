package pe.edu.unp.lanyan.identity.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.*;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.identity.application.UserService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @PostMapping("/users")
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        var user = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @GetMapping("/users")
    public PageView<UserView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.list(page, size);
    }

    @GetMapping("/users/{id}")
    public UserView get(@PathVariable UUID id) { return users.current(id); }

    @PutMapping("/users/{id}/roles")
    public UserView roles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request,
            @AuthenticationPrincipal AccountPrincipal principal) {
        return users.assignRoles(id, request.roles(), principal.getId());
    }

    @GetMapping("/roles")
    public List<RoleView> roles() { return users.listRoles(); }
}
