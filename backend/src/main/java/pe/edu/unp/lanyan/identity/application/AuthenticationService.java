package pe.edu.unp.lanyan.identity.application;

import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.LoginRequest;

@Service
@Profile("!bootstrap-admin")
public class AuthenticationService {
    private final AuthenticationManager manager;
    private final LoginAttemptLimiter limiter;

    public AuthenticationService(AuthenticationManager manager, LoginAttemptLimiter limiter) {
        this.manager = manager;
        this.limiter = limiter;
    }

    public Authentication login(LoginRequest request, String remoteAddress) {
        limiter.check(remoteAddress);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }
        return manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
    }
}
