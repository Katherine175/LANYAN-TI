package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
class OperationsTest {
    // Personas FICTICIAS; servicios del catálogo inicial (V7).
    static final String PASSWORD = "Fictitious-Operations-Test42!";
    static final String TECH = "tec-ops@unp.edu.pe", STUDENT = "est-ops@unp.edu.pe";
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
        users.saveAndFlush(new UserAccount("Técnico FICTICIO", TECH, hash, roles.findAllByNameIn(Set.of(RoleName.ROLE_TECHNICIAN))));
        users.saveAndFlush(new UserAccount("Estudiante FICTICIO", STUDENT, hash, roles.findAllByNameIn(Set.of(RoleName.ROLE_STUDENT))));
    }

    String serviceId(String name) { return jdbc.queryForObject("SELECT id::text FROM catalog_services WHERE name = ?", String.class, name); }

    HttpResponse<String> setStatus(Client client, String serviceId, String status) throws Exception {
        Integer version = JsonPath.read(client.get("/api/v1/catalog/services/" + serviceId).body(), "$.version");
        return client.send("PUT", "/api/v1/catalog/services/" + serviceId + "/operational-status", "{\"status\":\"" + status + "\",\"version\":" + version + "}");
    }

    @Test
    void anOutageCreatesAnExceptionEventThatBecomesAnIncidentAndCountsAgainstAvailability() throws Exception {
        var service = serviceId("Plataforma virtual");
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(setStatus(tech, service, "UNAVAILABLE").statusCode()).isEqualTo(200);

            var events = tech.get("/api/v1/operations/events").body();
            String eventId = JsonPath.<List<String>>read(events, "$[?(@.severity=='EXCEPTION' && @.serviceName=='Plataforma virtual')].id").get(0);
            assertThat(JsonPath.<List<String>>read(events, "$[?(@.id=='" + eventId + "')].status")).containsExactly("OPEN");
            assertThat(JsonPath.<List<String>>read(events, "$[?(@.id=='" + eventId + "')].source")).containsExactly("AUTOMATIC");

            var withIncident = tech.send("POST", "/api/v1/operations/events/" + eventId + "/incident", null);
            assertThat(withIncident.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(withIncident.body(), "$.ticketCode")).startsWith("INC-");
            assertThat(JsonPath.<String>read(withIncident.body(), "$.status")).isEqualTo("ACKNOWLEDGED");
            assertThat(tech.send("POST", "/api/v1/operations/events/" + eventId + "/incident", null).statusCode()).isEqualTo(409);

            var down = tech.get("/api/v1/operations/availability").body();
            assertThat(JsonPath.<List<String>>read(down, "$[?(@.serviceName=='Plataforma virtual')].currentStatus")).containsExactly("UNAVAILABLE");
            assertThat(JsonPath.<List<Integer>>read(down, "$[?(@.serviceName=='Plataforma virtual')].outages")).containsExactly(1);

            assertThat(setStatus(tech, service, "OPERATIONAL").statusCode()).isEqualTo(200);
            var after = tech.get("/api/v1/operations/events").body();
            assertThat(JsonPath.<List<String>>read(after, "$[?(@.id=='" + eventId + "')].status")).containsExactly("CLOSED");
            assertThat(JsonPath.<List<String>>read(after, "$[?(@.severity=='INFO' && @.serviceName=='Plataforma virtual')].title"))
                    .containsExactly("Plataforma virtual restablecido");
            assertThat(JsonPath.<List<Object>>read(tech.get("/api/v1/operations/availability").body(),
                    "$[?(@.serviceName=='Plataforma virtual')].mtrsMinutes")).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM service_status_changes WHERE service_id = ?::uuid", Long.class, service)).isEqualTo(2);
    }

    @Test
    void manualEventsAreAcknowledgedAndClosedAndNeedAServiceToBecomeIncidents() throws Exception {
        try (var tech = new Client()) {
            tech.login(TECH);
            var created = tech.send("POST", "/api/v1/operations/events", "{\"severity\":\"WARNING\",\"title\":\"Disco del servidor al 85 %\",\"detail\":\"Alerta del monitoreo\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(JsonPath.<String>read(created.body(), "$.code")).startsWith("EVT-");
            assertThat(JsonPath.<String>read(tech.send("POST", "/api/v1/operations/events/" + id + "/incident", null).body(), "$.code")).isEqualTo("EVENT_WITHOUT_SERVICE");
            assertThat(JsonPath.<String>read(tech.send("POST", "/api/v1/operations/events/" + id + "/acknowledge", null).body(), "$.status")).isEqualTo("ACKNOWLEDGED");
            assertThat(tech.send("POST", "/api/v1/operations/events/" + id + "/acknowledge", null).statusCode()).isEqualTo(409);
            assertThat(JsonPath.<String>read(tech.send("POST", "/api/v1/operations/events/" + id + "/close", null).body(), "$.status")).isEqualTo("CLOSED");
            assertThat(tech.send("POST", "/api/v1/operations/events", "{\"severity\":\"PANIC\",\"title\":\"x\"}").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void maintenanceIsScheduledInTheFutureAndCanBeCancelled() throws Exception {
        var service = serviceId("Internet y Wi-Fi");
        var start = Instant.now().plus(Duration.ofDays(2));
        try (var tech = new Client()) {
            tech.login(TECH);
            var past = "{\"serviceId\":\"" + service + "\",\"title\":\"Cambio de router\",\"startsAt\":\"" + Instant.now().minus(Duration.ofHours(1))
                    + "\",\"endsAt\":\"" + Instant.now() + "\",\"impact\":\"MEDIUM\"}";
            assertThat(tech.send("POST", "/api/v1/operations/maintenance", past).statusCode()).isEqualTo(400);
            var body = "{\"serviceId\":\"" + service + "\",\"title\":\"Cambio de router\",\"startsAt\":\"" + start
                    + "\",\"endsAt\":\"" + start.plus(Duration.ofHours(2)) + "\",\"impact\":\"MEDIUM\"}";
            var created = tech.send("POST", "/api/v1/operations/maintenance", body);
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(JsonPath.<List<String>>read(tech.get("/api/v1/operations/maintenance").body(), "$[*].id")).contains(id);
            assertThat(tech.send("POST", "/api/v1/operations/maintenance/" + id + "/cancel", null).statusCode()).isEqualTo(204);
            assertThat(tech.send("POST", "/api/v1/operations/maintenance/" + id + "/cancel", null).statusCode()).isEqualTo(409);
        }
    }

    @Test
    void requestersCannotSeeOperations() throws Exception {
        try (var student = new Client()) {
            student.login(STUDENT);
            assertThat(student.get("/api/v1/operations/events").statusCode()).isEqualTo(403);
            assertThat(student.get("/api/v1/operations/availability").statusCode()).isEqualTo(403);
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
