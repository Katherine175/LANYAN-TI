package pe.edu.unp.lanyan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lanyan.auth.login-limit=500")
class AuditTrailTest {
    static final String PASSWORD = "Fictitious-Test-Password42!";
    static final String ADMIN = "admin-audit@unp.edu.pe";
    static final String STUDENT = "student-audit@unp.edu.pe";

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

    @BeforeEach
    void seed() {
        if (users.count() != 0) return;
        users.saveAndFlush(new UserAccount("Admin FICTICIO", ADMIN, passwords.encode(PASSWORD),
                roles.findAllByNameIn(Set.of(RoleName.ROLE_ADMIN))));
        users.saveAndFlush(new UserAccount("Estudiante FICTICIO", STUDENT, passwords.encode(PASSWORD),
                roles.findAllByNameIn(Set.of(RoleName.ROLE_STUDENT))));
    }

    private int count(String action, String email) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action=? AND actor_email=?", Integer.class, action, email);
    }

    @Test
    void successfulLoginAndLogoutAreRecorded() throws Exception {
        int logins = count("LOGIN_SUCCEEDED", STUDENT);
        int logouts = count("LOGOUT", STUDENT);
        try (var client = new Client()) {
            client.login(STUDENT, PASSWORD);
            assertThat(client.send("POST", "/api/v1/auth/logout", null).statusCode()).isEqualTo(204);
        }
        assertThat(count("LOGIN_SUCCEEDED", STUDENT)).isEqualTo(logins + 1);
        assertThat(count("LOGOUT", STUDENT)).isEqualTo(logouts + 1);
    }

    @Test
    void failedLoginIsRecordedWithoutThePassword() throws Exception {
        int failures = count("LOGIN_FAILED", STUDENT);
        var wrong = "Wrong-Password-Value-99!";
        try (var client = new Client()) {
            assertThat(client.login(STUDENT, wrong).statusCode()).isEqualTo(401);
        }
        assertThat(count("LOGIN_FAILED", STUDENT)).isEqualTo(failures + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE detail LIKE ?", Integer.class, "%" + wrong + "%")).isZero();
    }

    @Test
    void userCreationAndRoleChangesRecordTheAdministrator() throws Exception {
        try (var client = new Client()) {
            client.login(ADMIN, PASSWORD);
            var created = client.send("POST", "/api/v1/users", "{\"fullName\":\"Cuenta FICTICIA\",\"email\":\"audited@unp.edu.pe\",\"password\":\""
                    + PASSWORD + "\",\"roles\":[\"ROLE_USER\"]}");
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(client.send("PUT", "/api/v1/users/" + id + "/roles", "{\"roles\":[\"ROLE_TECHNICIAN\"]}").statusCode()).isEqualTo(200);

            assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE target_id=? AND actor_email=?", String.class, id, ADMIN))
                    .containsExactlyInAnyOrder("USER_CREATED", "USER_ROLES_CHANGED");

            var page = client.send("GET", "/api/v1/audit/events?page=0&size=100", null);
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(page.body(), "$.content[*].action")).contains("USER_CREATED", "USER_ROLES_CHANGED");
        }
    }

    @Test
    void rejectedUserCreationLeavesNoRecord() throws Exception {
        try (var client = new Client()) {
            client.login(ADMIN, PASSWORD);
            var before = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action='USER_CREATED'", Integer.class);
            var duplicate = client.send("POST", "/api/v1/users", "{\"fullName\":\"Repetida\",\"email\":\"" + STUDENT + "\",\"password\":\""
                    + PASSWORD + "\",\"roles\":[\"ROLE_USER\"]}");
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action='USER_CREATED'", Integer.class)).isEqualTo(before);
        }
    }

    @Test
    void onlyAdministratorsCanReadTheAuditLog() throws Exception {
        try (var anonymous = new Client()) {
            assertThat(anonymous.send("GET", "/api/v1/audit/events", null).statusCode()).isEqualTo(401);
        }
        try (var student = new Client()) {
            student.login(STUDENT, PASSWORD);
            assertThat(student.send("GET", "/api/v1/audit/events", null).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void theApiCannotModifyTheAuditLog() throws Exception {
        try (var client = new Client()) {
            client.login(ADMIN, PASSWORD);
            assertThat(client.send("POST", "/api/v1/audit/events", "{}").statusCode()).isEqualTo(405);
            assertThat(client.send("DELETE", "/api/v1/audit/events", null).statusCode()).isEqualTo(405);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE audit_events SET detail='alterado'", "DELETE FROM audit_events", "TRUNCATE audit_events"})
    void theDatabaseRejectsAlteringTheAuditLog(String statement) throws Exception {
        try (var client = new Client()) {
            client.login(STUDENT, PASSWORD);
        }
        assertThatThrownBy(() -> jdbc.execute(statement)).hasMessageContaining("solo admite inserciones");
    }

    private final class Client implements AutoCloseable {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header = "X-CSRF-TOKEN";

        void csrf() throws Exception {
            var response = send("GET", "/api/v1/auth/csrf", null);
            token = JsonPath.read(response.body(), "$.token");
            header = JsonPath.read(response.body(), "$.headerName");
        }

        HttpResponse<String> login(String email, String password) throws Exception {
            csrf();
            var response = send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
            if (response.statusCode() == 200) csrf();
            return response;
        }

        HttpResponse<String> send(String method, String path, String json) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            if (json != null) request.header("Content-Type", "application/json");
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Override public void close() { http.close(); }
    }
}
