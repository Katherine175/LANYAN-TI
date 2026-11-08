package pe.edu.unp.lanyan.identity.infrastructure;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import pe.edu.unp.lanyan.identity.domain.UserAccount;

/** La sesión contiene una identidad serializable, nunca una entidad JPA. */
public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final UUID id;

    public AccountPrincipal(UserAccount account, boolean includePassword) {
        super(account.getEmail(), includePassword ? account.getPasswordHash() : "", account.isActive(),
                true, true, true, authorities(account));
        this.id = account.getId();
    }

    public UUID getId() { return id; }

    private static Set<SimpleGrantedAuthority> authorities(UserAccount account) {
        return account.getRoles().stream().flatMap(role -> Stream.concat(
                Stream.of(role.getName().name()), role.getPermissions().stream().map(permission -> permission.getName())))
                .map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }
}
