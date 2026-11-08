package pe.edu.unp.lanyan.identity.infrastructure;

import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.identity.domain.UserAccount;

@Service
@Transactional(readOnly = true)
public class AccountDetailsService implements UserDetailsService {
    private final UserAccountRepository users;

    public AccountDetailsService(UserAccountRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(UserAccount.normalizeEmail(email))
                .map(account -> new AccountPrincipal(account, true))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
    }

    public AccountPrincipal refresh(UUID id) {
        return users.findById(id).filter(UserAccount::isActive)
                .map(account -> new AccountPrincipal(account, false))
                .orElseThrow(() -> new UsernameNotFoundException("La sesión ya no es válida."));
    }
}
