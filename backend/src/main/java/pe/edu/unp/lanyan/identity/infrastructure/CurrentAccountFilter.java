package pe.edu.unp.lanyan.identity.infrastructure;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revalida estado y permisos en cada petición; una sesión no conserva roles obsoletos. */
public final class CurrentAccountFilter extends OncePerRequestFilter {
    private final AccountDetailsService accounts;
    public CurrentAccountFilter(AccountDetailsService accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            try {
                var current = accounts.refresh(principal.getId());
                var refreshed = UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities());
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(refreshed);
                SecurityContextHolder.setContext(context);
            } catch (UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityResponses.write(response, 401, "SESSION_INVALID");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
