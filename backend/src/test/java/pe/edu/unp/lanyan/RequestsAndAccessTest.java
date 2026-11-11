package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "lanyan.auth.login-limit=500")
class RequestsAndAccessTest {
    // Personas FICTICIAS; servicios y recursos de las migraciones V7 y V13.
    static final String PASSWORD = "Fictitious-Requests-Test42!";
    static final String STUDENT = "est-sol@unp.edu.pe", OTHER = "otro-sol@unp.edu.pe", TECH = "tec-sol@unp.edu.pe", ADMIN = "adm-sol@unp.edu.pe";
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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
    void fixture() {
        if (users.count() != 0) return;
        var hash = passwords.encode(PASSWORD);
        account(STUDENT, RoleName.ROLE_STUDENT, hash);
        account(OTHER, RoleName.ROLE_USER, hash);
        account(TECH, RoleName.ROLE_TECHNICIAN, hash);
        account(ADMIN, RoleName.ROLE_ADMIN, hash);
    }

    void account(String email, RoleName role, String hash) {
        users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(Set.of(role))));
    }

    String id(String sql, String name) { return jdbc.queryForObject(sql, String.class, name); }
    String serviceId(String name) { return id("SELECT id::text FROM catalog_services WHERE name = ?", name); }
    String resourceId(String name) { return id("SELECT id::text FROM access_resources WHERE name = ?", name); }
    static LocalDate today() { return LocalDate.now(ZoneId.of("America/Lima")); }

    String requestBody(String neededBy) {
        return "{\"serviceId\":\"" + serviceId("Proyectores") + "\",\"title\":\"Proyector para examen oral\",\"justification\":\"Aula 204, sección B1 FICTICIA\""
                + (neededBy == null ? "" : ",\"neededBy\":\"" + neededBy + "\"") + "}";
    }

    String accessBody(String resource, String level, String expiresOn) {
        return "{\"resourceId\":\"" + resourceId(resource) + "\",\"level\":\"" + level + "\",\"justification\":\"Docente FICTICIO de la sección A\""
                + (expiresOn == null ? "" : ",\"expiresOn\":\"" + expiresOn + "\"") + "}";
    }

    @Test
    void serviceRequestGoesFromApprovalToFulfillment() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            assertThat(student.send("POST", "/api/v1/service-requests", requestBody(today().minusDays(1).toString())).statusCode()).isEqualTo(400);
            var created = student.send("POST", "/api/v1/service-requests", requestBody(today().plusDays(3).toString()));
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(created.body(), "$.code")).startsWith("SOL-");
            assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("PENDING");
            id = JsonPath.read(created.body(), "$.id");
            assertThat(student.get("/api/v1/service-requests").statusCode()).isEqualTo(403);
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(JsonPath.<List<String>>read(tech.get("/api/v1/service-requests").body(), "$.content[*].id")).contains(id);
            assertThat(tech.send("POST", "/api/v1/service-requests/" + id + "/decision", "{\"approved\":true}").statusCode()).isEqualTo(403);
            assertThat(tech.send("POST", "/api/v1/service-requests/" + id + "/take", null).statusCode()).isEqualTo(409);
        }
        try (var admin = new Client()) {
            admin.login(ADMIN);
            assertThat(admin.send("POST", "/api/v1/service-requests/" + id + "/decision", "{\"approved\":false}").statusCode()).isEqualTo(400);
            var approved = admin.send("POST", "/api/v1/service-requests/" + id + "/decision", "{\"approved\":true,\"note\":\"Coordinar con el aula\"}");
            assertThat(JsonPath.<String>read(approved.body(), "$.status")).isEqualTo("APPROVED");
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(JsonPath.<String>read(tech.send("POST", "/api/v1/service-requests/" + id + "/take", null).body(), "$.status")).isEqualTo("IN_PROGRESS");
            assertThat(tech.send("POST", "/api/v1/service-requests/" + id + "/fulfill", "{\"note\":\"\"}").statusCode()).isEqualTo(400);
            var done = tech.send("POST", "/api/v1/service-requests/" + id + "/fulfill", "{\"note\":\"Proyector instalado y probado\"}");
            assertThat(JsonPath.<String>read(done.body(), "$.status")).isEqualTo("FULFILLED");
            assertThat(JsonPath.<String>read(done.body(), "$.fulfilledAt")).isNotNull();
        }
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE target_type='SERVICE_REQUEST' AND target_id=?", String.class, id))
                .contains("SERVICE_REQUEST_CREATED", "SERVICE_REQUEST_APPROVED", "SERVICE_REQUEST_ASSIGNED", "SERVICE_REQUEST_FULFILLED");
    }

    @Test
    void onlyTheRequesterCancelsAndOnlyWhilePending() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            id = JsonPath.read(student.send("POST", "/api/v1/service-requests", requestBody(null)).body(), "$.id");
        }
        try (var other = new Client()) {
            other.login(OTHER);
            assertThat(other.send("POST", "/api/v1/service-requests/" + id + "/cancel", null).statusCode()).isEqualTo(404);
            assertThat(JsonPath.<List<String>>read(other.get("/api/v1/service-requests/mine").body(), "$.content[*].id")).doesNotContain(id);
        }
        try (var student = new Client()) {
            student.login(STUDENT);
            assertThat(JsonPath.<String>read(student.send("POST", "/api/v1/service-requests/" + id + "/cancel", null).body(), "$.status")).isEqualTo("CANCELLED");
            assertThat(student.send("POST", "/api/v1/service-requests/" + id + "/cancel", null).statusCode()).isEqualTo(409);
        }
    }

    @Test
    void accessIsGrantedVerifiedAndRevoked() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            assertThat(JsonPath.<List<String>>read(student.get("/api/v1/access/resources").body(), "$[*].name")).hasSize(5).contains("Aula virtual");
            assertThat(student.send("POST", "/api/v1/access/requests", accessBody("Aula virtual", "EDIT", today().toString())).statusCode()).isEqualTo(400);
            var created = student.send("POST", "/api/v1/access/requests", accessBody("Aula virtual", "EDIT", today().plusMonths(4).toString()));
            assertThat(created.statusCode()).isEqualTo(201);
            id = JsonPath.read(created.body(), "$.id");
            assertThat(student.send("POST", "/api/v1/access/requests", accessBody("Aula virtual", "READ", null)).statusCode()).isEqualTo(409);
        }
        try (var admin = new Client()) {
            admin.login(ADMIN);
            var granted = admin.send("POST", "/api/v1/access/requests/" + id + "/decision", "{\"approved\":true}");
            assertThat(JsonPath.<String>read(granted.body(), "$.status")).isEqualTo("APPROVED");
            assertThat(JsonPath.<Boolean>read(granted.body(), "$.active")).isTrue();
            assertThat(admin.send("POST", "/api/v1/access/requests/" + id + "/revoke", "{\"reason\":\"no\"}").statusCode()).isEqualTo(400);
            var revoked = admin.send("POST", "/api/v1/access/requests/" + id + "/revoke", "{\"reason\":\"Terminó el periodo académico\"}");
            assertThat(JsonPath.<String>read(revoked.body(), "$.status")).isEqualTo("REVOKED");
            assertThat(JsonPath.<Boolean>read(revoked.body(), "$.active")).isFalse();
        }
        try (var student = new Client()) {
            student.login(STUDENT);
            assertThat(student.send("POST", "/api/v1/access/requests", accessBody("Aula virtual", "READ", null)).statusCode()).isEqualTo(201);
        }
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE target_type='ACCESS_REQUEST' AND target_id=?", String.class, id))
                .contains("ACCESS_REQUESTED", "ACCESS_GRANTED", "ACCESS_REVOKED");
    }

    @Test
    void accessIsNotGrantedToAnInactiveAccountAndOnlyAdminDecides() throws Exception {
        var hash = passwords.encode(PASSWORD);
        var email = "inactiva-" + UUID.randomUUID().toString().substring(0, 6) + "@unp.edu.pe";
        account(email, RoleName.ROLE_USER, hash);
        String id;
        try (var requester = new Client()) {
            requester.login(email);
            id = JsonPath.read(requester.send("POST", "/api/v1/access/requests", accessBody("Sistema de pagos", "READ", null)).body(), "$.id");
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(JsonPath.<List<String>>read(tech.get("/api/v1/access/requests").body(), "$.content[*].id")).contains(id);
            assertThat(tech.send("POST", "/api/v1/access/requests/" + id + "/decision", "{\"approved\":true}").statusCode()).isEqualTo(403);
        }
        jdbc.update("UPDATE identity_users SET active = false WHERE email = ?", email);
        try (var admin = new Client()) {
            admin.login(ADMIN);
            var response = admin.send("POST", "/api/v1/access/requests/" + id + "/decision", "{\"approved\":true}");
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("REQUESTER_INACTIVE");
            assertThat(admin.send("POST", "/api/v1/access/requests/" + id + "/decision", "{\"approved\":false,\"note\":\"Cuenta desactivada\"}").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void anonymousIsRejected() throws Exception {
        try (var client = new Client()) {
            assertThat(client.get("/api/v1/service-requests/mine").statusCode()).isEqualTo(401);
            assertThat(client.get("/api/v1/access/resources").statusCode()).isEqualTo(401);
        }
    }

    private final class Client implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header = "X-CSRF-TOKEN";
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }
        void csrf() throws Exception {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            token = JsonPath.read(response.body(), "$.token");
            header = JsonPath.read(response.body(), "$.headerName");
        }
        void login(String email) throws Exception {
            csrf();
            var response = send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
            assertThat(response.statusCode()).isEqualTo(200);
            csrf();
        }
        HttpResponse<String> send(String method, String path, String json) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getRequiredProperty("local.server.port") + path))
                    .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) request.header("Content-Type", "application/json");
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
