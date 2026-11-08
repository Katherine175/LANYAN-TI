package pe.edu.unp.lanyan.identity.application;

import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.LoginRequest;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@Service
@Profile("!bootstrap-admin")
public class AuthenticationService {
    private final AuthenticationManager manager;
    private final LoginAttemptLimiter limiter;
    private final AuditService audit;

    public AuthenticationService(AuthenticationManager manager, LoginAttemptLimiter limiter, AuditService audit) {
        this.manager = manager;
        this.limiter = limiter;
        this.audit = audit;
    }

    public Authentication login(LoginRequest request, String remoteAddress) {
        limiter.check(remoteAddress);
        try {
            if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new BadCredentialsException("Credenciales inválidas.");
            }
            var result = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            var principal = (AccountPrincipal) result.getPrincipal();
            audit.recordAuthentication(AuditAction.LOGIN_SUCCEEDED, principal.getId(), principal.getUsername());
            return result;
        } catch (AuthenticationException ex) {
            audit.recordAuthentication(AuditAction.LOGIN_FAILED, null, UserAccount.normalizeEmail(request.email()));
            throw ex;
        }
    }
}
