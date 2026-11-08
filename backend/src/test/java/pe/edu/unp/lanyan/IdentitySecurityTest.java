package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.identity.application.BootstrapAdminProvisioner;
import pe.edu.unp.lanyan.identity.application.UserService;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.AccountDetailsService;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lanyan.auth.login-limit=500")
class IdentitySecurityTest {
    // Datos exclusivamente ficticios en PostgreSQL temporal.
    static final String PASSWORD = "Fictitious-Test-Password42!";
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired Environment environment;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired BootstrapAdminProvisioner bootstrap;
    @Autowired UserService userService;
    @Autowired AccountDetailsService accounts;

    @BeforeEach
    void seedFictitiousAccounts() {
        if (users.count() != 0) return;
        bootstrap.provision("Administrador FICTICIO", email(RoleName.ROLE_ADMIN), PASSWORD);
        for (var role : RoleName.values()) {
            if (role != RoleName.ROLE_ADMIN) seed(email(role), Set.of(role));
        }
        seed("inactive@example.test", Set.of(RoleName.ROLE_USER));
        jdbc.update("UPDATE identity_users SET active=false WHERE email=?", "inactive@example.test");
    }

    private String email(RoleName role) { return role.name().toLowerCase(java.util.Locale.ROOT) + "@example.test"; }

    private UserAccount seed(String email, Set<RoleName> names) {
        return users.saveAndFlush(new UserAccount("Persona FICTICIA", email, passwords.encode(PASSWORD), roles.findAllByNameIn(names)));
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void eachRoleCanLoginAndReadOnlyItsOwnIdentity(RoleName role) throws Exception {
        try (var client = new Client()) {
            var login = client.login(email(role), PASSWORD);
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.body()).doesNotContain("password", "passwordHash");
            assertThat(JsonPath.<java.util.List<String>>read(login.body(), "$.roles")).containsExactly(role.name());
            assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
            int adminStatus = role == RoleName.ROLE_ADMIN ? 200 : 403;
            assertThat(client.get("/api/v1/users").statusCode()).isEqualTo(adminStatus);
            assertThat(client.get("/api/v1/roles").statusCode()).isEqualTo(adminStatus);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "missing-email", "disabled"})
    void invalidCredentialsAreRejectedWithoutDisclosingAccountState(String scenario) throws Exception {
        try (var client = new Client()) {
            String email = switch (scenario) {
                case "missing-email" -> "not-found@example.test";
                case "disabled" -> "inactive@example.test";
                default -> email(RoleName.ROLE_USER);
            };
            var response = client.login(email, scenario.equals("wrong-password") ? "Incorrect-Password42!" : PASSWORD);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_CREDENTIALS");
            assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void anonymousRequestsCannotReadOrCreateUsers() throws Exception {
        long count = users.count();
        try (var client = new Client()) {
            assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            assertThat(client.get("/api/v1/users").statusCode()).isEqualTo(401);
            client.csrf();
            assertThat(client.send("POST", "/api/v1/users", createBody("anonymous@example.test", "ROLE_ADMIN")).statusCode()).isEqualTo(401);
            assertThat(client.send("POST", "/api/v1/auth/register", "{}").statusCode()).isEqualTo(401);
        }
        assertThat(users.count()).isEqualTo(count);
    }

    @Test
    void csrfSessionFixationRotationAndLogoutAreEnforced() throws Exception {
        try (var client = new Client()) {
            assertThat(client.send("POST", "/api/v1/auth/login", loginBody(email(RoleName.ROLE_ADMIN), PASSWORD)).statusCode()).isEqualTo(403);
            var initialCsrf = client.csrf();
            var initialSession = client.sessionId();
            var login = client.send("POST", "/api/v1/auth/login", loginBody(email(RoleName.ROLE_ADMIN), PASSWORD));
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(client.sessionId()).isNotEqualTo(initialSession);
            assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax");
            client.token = initialCsrf;
            assertThat(client.send("POST", "/api/v1/auth/logout", "{}").statusCode()).isEqualTo(403);
            client.csrf();
            assertThat(client.send("POST", "/api/v1/auth/logout", "{}").statusCode()).isEqualTo(204);
            assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            client.csrf();
            client.token = "invalid-csrf-token";
            assertThat(client.send("POST", "/api/v1/auth/login", loginBody(email(RoleName.ROLE_ADMIN), PASSWORD)).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void adminCreatesMultiRoleUserWithHashedPasswordAndRejectsDuplicates() throws Exception {
        String email = "created-" + UUID.randomUUID() + "@example.test";
        try (var admin = new Client()) {
            assertThat(admin.login(email(RoleName.ROLE_ADMIN), PASSWORD).statusCode()).isEqualTo(200);
            var created = admin.send("POST", "/api/v1/users", createBody(email, "ROLE_STUDENT", "ROLE_USER"));
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(created.headers().firstValue("location")).isPresent();
            assertThat(created.body()).doesNotContain("password", PASSWORD);
            var stored = users.findByEmail(email).orElseThrow();
            assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
            assertThat(passwords.matches(PASSWORD, stored.getPasswordHash())).isTrue();
            assertThat(admin.send("POST", "/api/v1/users", createBody(email, "ROLE_USER")).statusCode()).isEqualTo(409);
            try (var student = new Client()) {
                var login = student.login(email, PASSWORD);
                assertThat(login.statusCode()).isEqualTo(200);
                assertThat(JsonPath.<java.util.List<String>>read(login.body(), "$.roles")).containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_USER");
                assertThat(JsonPath.<java.util.List<String>>read(login.body(), "$.permissions")).contains("portal:academic:access", "portal:it:access");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ROLE_USER", "ROLE_STUDENT", "ROLE_TECHNICIAN"})
    void nonAdminsCannotCreateUsersOrAssignRoles(RoleName role) throws Exception {
        var adminId = users.findByEmail(email(RoleName.ROLE_ADMIN)).orElseThrow().getId();
        try (var client = new Client()) {
            client.login(email(role), PASSWORD);
            assertThat(client.send("POST", "/api/v1/users", createBody("forbidden@example.test", "ROLE_ADMIN")).statusCode()).isEqualTo(403);
            assertThat(client.send("PUT", "/api/v1/users/" + adminId + "/roles", "{\"roles\":[\"ROLE_STUDENT\"]}").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void roleChangesTakeEffectInAnExistingSession() throws Exception {
        var target = seed("demote-" + UUID.randomUUID() + "@example.test", Set.of(RoleName.ROLE_ADMIN, RoleName.ROLE_USER));
        try (var admin = new Client(); var targetSession = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            targetSession.login(target.getEmail(), PASSWORD);
            assertThat(targetSession.get("/api/v1/users").statusCode()).isEqualTo(200);
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId() + "/roles", "{\"roles\":[\"ROLE_STUDENT\",\"ROLE_TECHNICIAN\"]}").statusCode()).isEqualTo(200);
            assertThat(targetSession.get("/api/v1/users").statusCode()).isEqualTo(403);
            var me = targetSession.get("/api/v1/auth/me");
            assertThat(JsonPath.<java.util.List<String>>read(me.body(), "$.roles")).containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_TECHNICIAN");
            var adminId = users.findByEmail(email(RoleName.ROLE_ADMIN)).orElseThrow().getId();
            assertThat(admin.send("PUT", "/api/v1/users/" + adminId + "/roles", "{\"roles\":[\"ROLE_USER\"]}").statusCode()).isEqualTo(409);
        }
    }

    @Test
    void disablingAnAccountInvalidatesItsExistingSession() throws Exception {
        var target = seed("disable-" + UUID.randomUUID() + "@example.test", Set.of(RoleName.ROLE_USER));
        try (var client = new Client()) {
            client.login(target.getEmail(), PASSWORD);
            jdbc.update("UPDATE identity_users SET active=false WHERE id=?", target.getId());
            assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void validationAndErrorResponsesDoNotExposeSubmittedPasswords() throws Exception {
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            var invalid = admin.send("POST", "/api/v1/users", "{\"fullName\":\"\",\"email\":\"bad-email\",\"password\":\"short-secret\",\"roles\":[]}");
            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(invalid.body()).contains("VALIDATION_ERROR").doesNotContain("short-secret");
            assertThat(admin.get("/api/v1/users?size=0").statusCode()).isEqualTo(400);
            assertThat(admin.get("/api/v1/users/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(admin.send("POST", "/api/v1/users", createBody("invalid-role@example.test", "ROLE_UNKNOWN")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/users", "not-json").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void aStudentCannotUseTheServiceToReadAnotherUsersIdentity() {
        var student = users.findByEmail(email(RoleName.ROLE_STUDENT)).orElseThrow();
        var principal = accounts.refresh(student.getId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            var adminId = users.findByEmail(email(RoleName.ROLE_ADMIN)).orElseThrow().getId();
            assertThatThrownBy(() -> userService.current(adminId)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void bootstrapCannotCreateAnotherAdministratorInAPopulatedDatabase() {
        long count = users.count();
        assertThatThrownBy(() -> bootstrap.provision("Otra persona FICTICIA", "second@example.test", PASSWORD))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isEqualTo(count);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String createBody(String email, String... names) {
        String rolesJson = java.util.Arrays.stream(names).map(name -> "\"" + name + "\"").collect(java.util.stream.Collectors.joining(","));
        return "{\"fullName\":\"Cuenta FICTICIA\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"roles\":[" + rolesJson + "]}";
    }

    private final class Client implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header = "X-CSRF-TOKEN";

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }
        String csrf() throws Exception {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            token = JsonPath.read(response.body(), "$.token");
            header = JsonPath.read(response.body(), "$.headerName");
            return token;
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            csrf();
            var response = send("POST", "/api/v1/auth/login", loginBody(email, password));
            if (response.statusCode() == 200) csrf();
            return response;
        }
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("LANYAN_SESSION"))
                    .map(java.net.HttpCookie::getValue).findFirst().orElseThrow();
        }
        HttpResponse<String> send(String method, String path, String json) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) request.header("Content-Type", "application/json");
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
