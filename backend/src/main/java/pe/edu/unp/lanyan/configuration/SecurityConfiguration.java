package pe.edu.unp.lanyan.configuration;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.identity.infrastructure.AccountDetailsService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;
import pe.edu.unp.lanyan.identity.infrastructure.CurrentAccountFilter;
import pe.edu.unp.lanyan.identity.infrastructure.SecurityResponses;

@Configuration(proxyBeanMethods = false)
@Profile("!bootstrap-admin")
public class SecurityConfiguration {

    @Bean
    AuthenticationManager authenticationManager(AccountDetailsService accounts, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountDetailsService accounts,
            HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf, AuditService audit) throws Exception {
        return http
                .csrf(config -> config.csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(contexts))
                .authorizeHttpRequests(authorize -> authorize
                        // Sin esto, un error del servidor se reescribe como 401/403 al renderizar /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/students/me").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/students/me/courses").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/students/me/schedule", "/api/v1/students/me/materials").hasRole("STUDENT")
                        .requestMatchers("/api/v1/academic", "/api/v1/academic/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users", "/api/v1/users/**", "/api/v1/roles").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit", "/api/v1/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/catalog/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/catalog/services/*/operational-status").hasAnyRole("ADMIN", "TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/services", "/api/v1/catalog/services/*", "/api/v1/catalog/categories").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("LANYAN_SESSION")
                        .logoutSuccessHandler((request, response, auth) -> {
                            if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
                                audit.recordAuthentication(AuditAction.LOGOUT, principal.getId(), principal.getUsername());
                            }
                            response.setStatus(204);
                        }))
                .addFilterBefore(new CurrentAccountFilter(accounts), AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> SecurityResponses.write(response, 401, "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, ex) -> SecurityResponses.write(response, 403,
                                ex instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED")))
                .build();
    }
}
