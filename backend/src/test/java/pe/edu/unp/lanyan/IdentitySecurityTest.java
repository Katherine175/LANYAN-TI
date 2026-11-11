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
        seed("inactive@unp.edu.pe", Set.of(RoleName.ROLE_USER));
        jdbc.update("UPDATE identity_users SET active=false WHERE email=?", "inactive@unp.edu.pe");
    }

    private String email(RoleName role) { return role.name().toLowerCase(java.util.Locale.ROOT) + "@unp.edu.pe"; }

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
                case "missing-email" -> "not-found@unp.edu.pe";
                case "disabled" -> "inactive@unp.edu.pe";
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
            assertThat(client.send("POST", "/api/v1/users", createBody("anonymous@unp.edu.pe", "ROLE_ADMIN")).statusCode()).isEqualTo(401);
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
        String email = "created-" + UUID.randomUUID() + "@unp.edu.pe";
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
            assertThat(client.send("POST", "/api/v1/users", createBody("forbidden@unp.edu.pe", "ROLE_ADMIN")).statusCode()).isEqualTo(403);
            assertThat(client.send("PUT", "/api/v1/users/" + adminId + "/roles", "{\"roles\":[\"ROLE_STUDENT\"]}").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void roleChangesTakeEffectInAnExistingSession() throws Exception {
        var target = seed("demote-" + UUID.randomUUID() + "@unp.edu.pe", Set.of(RoleName.ROLE_ADMIN, RoleName.ROLE_USER));
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
        var target = seed("disable-" + UUID.randomUUID() + "@unp.edu.pe", Set.of(RoleName.ROLE_USER));
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
            assertThat(admin.send("POST", "/api/v1/users", createBody("invalid-role@unp.edu.pe", "ROLE_UNKNOWN")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/users", "not-json").statusCode()).isEqualTo(400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"persona@gmail.com", "persona@unp", "persona@alumnos.unp.edu.pe", "persona@unp.edu.pe.com"})
    void onlyInstitutionalEmailsCanBeUsedForNewAccounts(String email) throws Exception {
        long count = users.count();
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            var response = admin.send("POST", "/api/v1/users", createBody(email, "ROLE_USER"));
            assertThat(response.statusCode()).isEqualTo(400);
        }
        assertThat(users.count()).isEqualTo(count);
    }

    @Test
    void institutionalEmailIsAcceptedRegardlessOfCase() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            var created = admin.send("POST", "/api/v1/users", createBody("Mayus-" + suffix + "@UNP.EDU.PE", "ROLE_USER"));
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(created.body(), "$.email")).isEqualTo("mayus-" + suffix + "@unp.edu.pe");
        }
    }

    @Test
    void adminEditsNameEmailRolesStatusAndPassword() throws Exception {
        var target = seed("edit-" + UUID.randomUUID() + "@unp.edu.pe", Set.of(RoleName.ROLE_USER));
        String newEmail = "edited-" + UUID.randomUUID() + "@unp.edu.pe";
        String newPassword = "Another-Fictitious-Pass77!";
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            var updated = admin.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Nombre EDITADO", newEmail, true, newPassword, "ROLE_STUDENT", "ROLE_TECHNICIAN"));
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(updated.body()).doesNotContain(newPassword);
            assertThat(JsonPath.<String>read(updated.body(), "$.fullName")).isEqualTo("Nombre EDITADO");
            assertThat(JsonPath.<java.util.List<String>>read(updated.body(), "$.roles")).containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_TECHNICIAN");
            try (var edited = new Client()) {
                assertThat(edited.login(target.getEmail(), PASSWORD).statusCode()).isEqualTo(401);
                assertThat(edited.login(newEmail, newPassword).statusCode()).isEqualTo(200);
            }
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Nombre EDITADO", newEmail, false, null, "ROLE_STUDENT")).statusCode()).isEqualTo(200);
            try (var disabled = new Client()) {
                assertThat(disabled.login(newEmail, newPassword).statusCode()).isEqualTo(401);
            }
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Nombre EDITADO", "otro@gmail.com", true, null, "ROLE_STUDENT")).statusCode()).isEqualTo(400);
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Nombre EDITADO", email(RoleName.ROLE_USER), true, null, "ROLE_STUDENT")).statusCode()).isEqualTo(409);
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Nombre EDITADO", newEmail, true, "corta", "ROLE_STUDENT")).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void accountsCreatedBeforeTheDomainRuleCanStillBeEditedWithoutChangingTheirEmail() throws Exception {
        var legacy = seed("legacy-" + UUID.randomUUID() + "@example.test", Set.of(RoleName.ROLE_USER));
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            assertThat(admin.send("PUT", "/api/v1/users/" + legacy.getId(),
                    updateBody("Cuenta ANTIGUA", legacy.getEmail(), true, null, "ROLE_TECHNICIAN")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        var adminId = users.findByEmail(email(RoleName.ROLE_ADMIN)).orElseThrow().getId();
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            assertThat(admin.send("PUT", "/api/v1/users/" + adminId,
                    updateBody("Administrador FICTICIO", email(RoleName.ROLE_ADMIN), false, null, "ROLE_ADMIN")).statusCode()).isEqualTo(409);
            assertThat(admin.send("PUT", "/api/v1/users/" + adminId,
                    updateBody("Administrador FICTICIO", email(RoleName.ROLE_ADMIN), true, null, "ROLE_USER")).statusCode()).isEqualTo(409);
            assertThat(admin.send("DELETE", "/api/v1/users/" + adminId, null).statusCode()).isEqualTo(409);
            assertThat(admin.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void adminDeletesAccountsAndTheirSessionsEnd() throws Exception {
        var target = seed("delete-" + UUID.randomUUID() + "@unp.edu.pe", Set.of(RoleName.ROLE_USER));
        try (var admin = new Client(); var targetSession = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            targetSession.login(target.getEmail(), PASSWORD);
            assertThat(admin.send("DELETE", "/api/v1/users/" + target.getId(), null).statusCode()).isEqualTo(204);
            assertThat(users.findById(target.getId())).isEmpty();
            assertThat(targetSession.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
            assertThat(admin.send("DELETE", "/api/v1/users/" + target.getId(), null).statusCode()).isEqualTo(404);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'USER_DELETED' AND target_id = ?",
                    Long.class, target.getId().toString())).isEqualTo(1);
        }
    }

    @Test
    void studentsWithAcademicProfileAreDeactivatedInsteadOfDeleted() throws Exception {
        var student = seed("profile-" + UUID.randomUUID() + "@unp.edu.pe", Set.of(RoleName.ROLE_STUDENT));
        jdbc.update("INSERT INTO academic_student_profiles (id, user_id, student_code) VALUES (?, ?, ?)",
                UUID.randomUUID(), student.getId(), "FIC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN), PASSWORD);
            var response = admin.send("DELETE", "/api/v1/users/" + student.getId(), null);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("USER_HAS_ACADEMIC_PROFILE");
            assertThat(users.findById(student.getId())).isPresent();
        }
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ROLE_USER", "ROLE_STUDENT", "ROLE_TECHNICIAN"})
    void nonAdminsCannotEditOrDeleteUsers(RoleName role) throws Exception {
        var target = users.findByEmail("inactive@unp.edu.pe").orElseThrow();
        try (var client = new Client()) {
            client.login(email(role), PASSWORD);
            assertThat(client.send("PUT", "/api/v1/users/" + target.getId(),
                    updateBody("Intento", target.getEmail(), true, null, "ROLE_ADMIN")).statusCode()).isEqualTo(403);
            assertThat(client.send("DELETE", "/api/v1/users/" + target.getId(), null).statusCode()).isEqualTo(403);
        }
        assertThat(users.findById(target.getId())).isPresent();
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
        assertThatThrownBy(() -> bootstrap.provision("Otra persona FICTICIA", "second@unp.edu.pe", PASSWORD))
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

    private String updateBody(String fullName, String email, boolean active, String password, String... names) {
        String rolesJson = java.util.Arrays.stream(names).map(name -> "\"" + name + "\"").collect(java.util.stream.Collectors.joining(","));
        return "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"active\":" + active
                + (password == null ? "" : ",\"password\":\"" + password + "\"") + ",\"roles\":[" + rolesJson + "]}";
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
