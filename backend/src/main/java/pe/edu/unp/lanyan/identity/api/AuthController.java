package pe.edu.unp.lanyan.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.*;
import pe.edu.unp.lanyan.identity.application.AuthenticationService;
import pe.edu.unp.lanyan.identity.application.UserService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("!bootstrap-admin")
public class AuthController {
    private final AuthenticationService authentication;
    private final UserService users;
    private final SessionAuthenticationStrategy sessions;
    private final HttpSessionSecurityContextRepository contexts;

    public AuthController(AuthenticationService authentication, UserService users,
            SessionAuthenticationStrategy sessions, HttpSessionSecurityContextRepository contexts) {
        this.authentication = authentication;
        this.users = users;
        this.sessions = sessions;
        this.contexts = contexts;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken csrf) { return new CsrfView(csrf.getToken(), csrf.getHeaderName()); }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var authenticated = authentication.login(body, request.getRemoteAddr());
        sessions.onAuthentication(authenticated, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return users.current(((AccountPrincipal) authenticated.getPrincipal()).getId());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AccountPrincipal principal) { return users.current(principal.getId()); }
}
