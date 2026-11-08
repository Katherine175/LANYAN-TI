package pe.edu.unp.lanyan.identity.application;

import java.util.Set;
import java.util.List;
import java.util.EnumSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.RoleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.CreateUserRequest;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.UserView;
import pe.edu.unp.lanyan.identity.domain.Role;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import pe.edu.unp.lanyan.shared.api.PageView;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;

    public UserService(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwords, JdbcTemplate jdbc) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    @Transactional
    @PreAuthorize("hasAuthority('identity:users:create')")
    public UserView create(CreateUserRequest request) {
        PasswordPolicy.validateNewPassword(request.password());
        var email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new IdentityException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "El correo ya está registrado.");
        }
        var account = new UserAccount(request.fullName(), email, passwords.encode(request.password()), resolveRoles(request.roles()));
        return view(users.saveAndFlush(account));
    }

    @PreAuthorize("#id == authentication.principal.id or hasAuthority('identity:users:read')")
    public UserView current(UUID id) { return view(find(id)); }

    @PreAuthorize("hasAuthority('identity:users:read')")
    public PageView<UserView> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "La página debe ser positiva y el tamaño entre 1 y 100.");
        }
        return PageView.of(users.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))))
                .map(UserService::view));
    }

    @Transactional
    @PreAuthorize("hasAuthority('identity:roles:assign')")
    public UserView assignRoles(UUID id, Set<RoleName> requested, UUID actorId) {
        // Serializa cambios de roles para impedir que dos administradores se retiren el acceso entre sí.
        jdbc.execute("SELECT pg_advisory_xact_lock(764202601)");
        if (!find(actorId).getRoles().stream().anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN)) {
            throw new AccessDeniedException("Se requiere un administrador vigente.");
        }
        var resolved = resolveRoles(requested);
        var account = find(id);
        // El administrador conserva su propio acceso; otra cuenta administradora puede cambiarlo.
        if (actorId.equals(id) && !requested.contains(RoleName.ROLE_ADMIN)) {
            throw new IdentityException(HttpStatus.CONFLICT, "SELF_ADMIN_REMOVAL",
                    "No puedes retirar tu propio rol de administrador.");
        }
        account.replaceRoles(resolved);
        return view(users.saveAndFlush(account));
    }

    @PreAuthorize("hasAuthority('identity:roles:assign')")
    public List<RoleView> listRoles() {
        return roles.findAllByNameIn(EnumSet.allOf(RoleName.class)).stream()
                .sorted(java.util.Comparator.comparing(Role::getName))
                .map(role -> new RoleView(role.getName(), role.getPermissions().stream()
                        .map(permission -> permission.getName()).collect(Collectors.toSet())))
                .toList();
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> new IdentityException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
    }

    private Set<Role> resolveRoles(Set<RoleName> requested) {
        if (requested == null || requested.isEmpty() || requested.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "INVALID_ROLES", "Selecciona al menos un rol válido.");
        }
        var result = roles.findAllByNameIn(requested);
        if (result.size() != requested.size()) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "INVALID_ROLES", "Uno o más roles no existen.");
        }
        return result;
    }

    public static UserView view(UserAccount account) {
        var names = account.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
        var permissions = account.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName()).collect(Collectors.toCollection(TreeSet::new));
        return new UserView(account.getId(), account.getFullName(), account.getEmail(), account.isActive(), names, permissions);
    }
}
