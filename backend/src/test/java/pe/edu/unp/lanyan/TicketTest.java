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
import pe.edu.unp.lanyan.catalog.application.BusinessHours;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "lanyan.auth.login-limit=500")
class TicketTest {
    // Personas FICTICIAS; los servicios son los del catálogo inicial de V7 con la prioridad de V12.
    static final String PASSWORD = "Fictitious-Ticket-Test42!";
    static final String STUDENT = "est-ticket@unp.edu.pe", OTHER = "otro-ticket@unp.edu.pe", USER = "usuario-ticket@unp.edu.pe",
            TECH = "tecnico-ticket@unp.edu.pe", ADMIN = "admin-ticket@unp.edu.pe";
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
    @Autowired BusinessHours hours;

    @BeforeEach
    void fixture() {
        if (users.count() != 0) return;
        var hash = passwords.encode(PASSWORD);
        account(STUDENT, RoleName.ROLE_STUDENT, hash);
        account(OTHER, RoleName.ROLE_STUDENT, hash);
        account(USER, RoleName.ROLE_USER, hash);
        account(TECH, RoleName.ROLE_TECHNICIAN, hash);
        account(ADMIN, RoleName.ROLE_ADMIN, hash);
    }

    void account(String email, RoleName role, String hash) {
        users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(Set.of(role))));
    }

    String serviceId(String name) { return jdbc.queryForObject("SELECT id::text FROM catalog_services WHERE name = ?", String.class, name); }

    String ticketBody(String service, String extra) {
        return "{\"serviceId\":\"" + serviceId(service) + "\",\"title\":\"No carga el aula FICTICIA\",\"description\":\"Desde las 18:00\"" + extra + "}";
    }

    @Test
    void priorityComesFromTheServiceAndDeadlinesFromTheReportTimes() throws Exception {
        try (var student = new Client()) {
            student.login(STUDENT);
            // Aunque el solicitante intente marcar Crítica, manda el servicio.
            var created = student.send("POST", "/api/v1/tickets", ticketBody("Plataforma virtual", ",\"priority\":\"CRITICAL\""));
            assertThat(created.statusCode()).isEqualTo(201);
            var body = created.body();
            assertThat(JsonPath.<String>read(body, "$.code")).startsWith("INC-");
            assertThat(JsonPath.<String>read(body, "$.priority")).isEqualTo("HIGH");
            assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("NEW");
            assertThat(JsonPath.<String>read(body, "$.requesterEmail")).isEqualTo(STUDENT);

            var createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
            assertThat(Instant.parse(JsonPath.read(body, "$.firstResponseDue"))).isEqualTo(hours.plusBusinessMinutes(createdAt, 15));
            assertThat(Instant.parse(JsonPath.read(body, "$.resolutionDue"))).isEqualTo(hours.plusBusinessMinutes(createdAt, 60));
            assertThat(JsonPath.<String>read(body, "$.firstResponseSla")).isIn("ON_TIME", "AT_RISK");

            var low = student.send("POST", "/api/v1/tickets", ticketBody("Soporte técnico", ""));
            assertThat(JsonPath.<String>read(low.body(), "$.priority")).isEqualTo("LOW");
            var lowCreated = Instant.parse(JsonPath.read(low.body(), "$.createdAt"));
            assertThat(Instant.parse(JsonPath.read(low.body(), "$.resolutionDue"))).isEqualTo(hours.plusBusinessMinutes(lowCreated, 480));
            assertThat(JsonPath.<String>read(student.send("POST", "/api/v1/tickets", ticketBody("Matrícula", "")).body(), "$.priority")).isEqualTo("MEDIUM");
        }
    }

    @Test
    void requestersOnlySeeTheirOwnTickets() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            id = JsonPath.read(student.send("POST", "/api/v1/tickets", ticketBody("Pagos", "")).body(), "$.id");
            assertThat(JsonPath.<List<String>>read(student.get("/api/v1/tickets/mine").body(), "$.content[*].id")).contains(id);
            assertThat(student.get("/api/v1/tickets/" + id).statusCode()).isEqualTo(200);
            assertThat(student.get("/api/v1/tickets").statusCode()).isEqualTo(403);
        }
        try (var other = new Client()) {
            other.login(OTHER);
            assertThat(JsonPath.<List<String>>read(other.get("/api/v1/tickets/mine").body(), "$.content[*].id")).doesNotContain(id);
            assertThat(other.get("/api/v1/tickets/" + id).statusCode()).isEqualTo(404);
            assertThat(other.send("POST", "/api/v1/tickets/" + id + "/take", null).statusCode()).isEqualTo(403);
            assertThat(other.send("PUT", "/api/v1/tickets/" + id + "/status", "{\"status\":\"RESOLVED\"}").statusCode()).isEqualTo(403);
        }
        try (var user = new Client()) {
            user.login(USER);
            assertThat(user.send("POST", "/api/v1/tickets", ticketBody("Proyectores", "")).statusCode()).isEqualTo(201);
        }
    }

    @Test
    void technicianTakesResolvesAndEverythingIsAudited() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            id = JsonPath.read(student.send("POST", "/api/v1/tickets", ticketBody("Internet y Wi-Fi", "")).body(), "$.id");
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(JsonPath.<List<String>>read(tech.get("/api/v1/tickets?size=100").body(), "$.content[*].id")).contains(id);
            var taken = tech.send("POST", "/api/v1/tickets/" + id + "/take", null);
            assertThat(taken.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(taken.body(), "$.status")).isEqualTo("IN_PROGRESS");
            assertThat(JsonPath.<String>read(taken.body(), "$.assigneeName")).isEqualTo("Persona FICTICIA");
            assertThat(JsonPath.<String>read(taken.body(), "$.firstRespondedAt")).isNotNull();
            assertThat(JsonPath.<String>read(taken.body(), "$.firstResponseSla")).isEqualTo("MET");

            var resolved = tech.send("PUT", "/api/v1/tickets/" + id + "/status", "{\"status\":\"RESOLVED\"}");
            assertThat(JsonPath.<String>read(resolved.body(), "$.status")).isEqualTo("RESOLVED");
            assertThat(JsonPath.<String>read(resolved.body(), "$.resolutionSla")).isEqualTo("MET");
            assertThat(tech.send("PUT", "/api/v1/tickets/" + id + "/status", "{\"status\":\"NEW\"}").statusCode()).isEqualTo(400);
            // Reabrir borra la hora de resolución.
            var reopened = tech.send("PUT", "/api/v1/tickets/" + id + "/status", "{\"status\":\"IN_PROGRESS\"}");
            assertThat(JsonPath.<Object>read(reopened.body(), "$.resolvedAt")).isNull();
        }
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE target_type='TICKET' AND target_id=?", String.class, id))
                .contains("TICKET_CREATED", "TICKET_ASSIGNED", "TICKET_STATUS_CHANGED");
    }

    @Test
    void onlyStaffChangesPriorityWithAReasonAndDeadlinesFollow() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            id = JsonPath.read(student.send("POST", "/api/v1/tickets", ticketBody("Correo institucional", "")).body(), "$.id");
            assertThat(student.send("PUT", "/api/v1/tickets/" + id + "/priority", "{\"priority\":\"CRITICAL\",\"reason\":\"Me urge mucho\"}").statusCode()).isEqualTo(403);
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(tech.send("PUT", "/api/v1/tickets/" + id + "/priority", "{\"priority\":\"CRITICAL\",\"reason\":\"no\"}").statusCode()).isEqualTo(400);
            var critical = tech.send("PUT", "/api/v1/tickets/" + id + "/priority",
                    "{\"priority\":\"CRITICAL\",\"reason\":\"El correo cayó para todo el Instituto\"}");
            assertThat(critical.statusCode()).isEqualTo(200);
            var createdAt = Instant.parse(JsonPath.read(critical.body(), "$.createdAt"));
            assertThat(JsonPath.<String>read(critical.body(), "$.priority")).isEqualTo("CRITICAL");
            assertThat(Instant.parse(JsonPath.read(critical.body(), "$.firstResponseDue"))).isEqualTo(hours.plusBusinessMinutes(createdAt, 10));
            assertThat(Instant.parse(JsonPath.read(critical.body(), "$.resolutionDue"))).isEqualTo(hours.plusBusinessMinutes(createdAt, 30));
        }
        var detail = jdbc.queryForObject("SELECT detail FROM audit_events WHERE action='TICKET_PRIORITY_CHANGED' AND target_id=?", String.class, id);
        assertThat(detail).contains("MEDIUM a CRITICAL", "motivo=El correo cayó para todo el Instituto");
    }

    @Test
    void adminAdjustsSlaTimesAndTheCatalogFollows() throws Exception {
        try (var admin = new Client()) {
            admin.login(ADMIN);
            var policies = admin.get("/api/v1/sla/policies");
            assertThat(JsonPath.<List<String>>read(policies.body(), "$[*].priority")).containsExactly("CRITICAL", "HIGH", "MEDIUM", "LOW");
            assertThat(JsonPath.<List<Integer>>read(policies.body(), "$[*].resolutionMinutes")).containsExactly(30, 60, 240, 480);
            Integer version = JsonPath.read(policies.body(), "$[3].version");

            assertThat(admin.send("PUT", "/api/v1/sla/policies/LOW", "{\"firstResponseMinutes\":90,\"resolutionMinutes\":60,\"version\":" + version + "}").statusCode()).isEqualTo(400);
            var updated = admin.send("PUT", "/api/v1/sla/policies/LOW", "{\"firstResponseMinutes\":90,\"resolutionMinutes\":600,\"version\":" + version + "}");
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT response_target_minutes FROM catalog_services WHERE name='Soporte técnico'", Integer.class)).isEqualTo(90);
            assertThat(admin.send("PUT", "/api/v1/sla/policies/LOW", "{\"firstResponseMinutes\":60,\"resolutionMinutes\":480,\"version\":" + version + "}").statusCode()).isEqualTo(409);
            // Volver a los tiempos del informe para no afectar otras pruebas.
            Integer current = JsonPath.read(updated.body(), "$.version");
            assertThat(admin.send("PUT", "/api/v1/sla/policies/LOW", "{\"firstResponseMinutes\":60,\"resolutionMinutes\":480,\"version\":" + current + "}").statusCode()).isEqualTo(200);
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(tech.get("/api/v1/sla/policies").statusCode()).isEqualTo(200);
            assertThat(tech.send("PUT", "/api/v1/sla/policies/LOW", "{\"firstResponseMinutes\":1,\"resolutionMinutes\":1,\"version\":0}").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void criticalIsNeverAServiceDefault() throws Exception {
        var id = serviceId("Proyectores");
        try (var admin = new Client()) {
            admin.login(ADMIN);
            var service = admin.get("/api/v1/catalog/services/" + id);
            assertThat(JsonPath.<String>read(service.body(), "$.defaultPriority")).isEqualTo("MEDIUM");
            String category = JsonPath.read(service.body(), "$.category.id");
            Integer version = JsonPath.read(service.body(), "$.version");
            var body = "{\"name\":\"Proyectores\",\"description\":\"Reserva, instalación y soporte audiovisual.\",\"categoryId\":\"" + category
                    + "\",\"owner\":\"Service Desk TI\",\"icon\":\"PROJECTOR\",\"lifecycleStatus\":\"PUBLISHED\",\"availabilityTarget\":96.4,\"version\":" + version;
            assertThat(admin.send("PUT", "/api/v1/catalog/admin/services/" + id, body + ",\"defaultPriority\":\"CRITICAL\"}").statusCode()).isEqualTo(400);
            var high = admin.send("PUT", "/api/v1/catalog/admin/services/" + id, body + ",\"defaultPriority\":\"HIGH\"}");
            assertThat(high.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(high.body(), "$.responseTargetMinutes")).isEqualTo(15);
            Integer next = JsonPath.read(high.body(), "$.version");
            assertThat(admin.send("PUT", "/api/v1/catalog/admin/services/" + id, body.replace("\"version\":" + version, "\"version\":" + next) + ",\"defaultPriority\":\"MEDIUM\"}").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void anonymousCannotUseTickets() throws Exception {
        try (var client = new Client()) {
            assertThat(client.get("/api/v1/tickets/mine").statusCode()).isEqualTo(401);
            assertThat(client.get("/api/v1/sla/policies").statusCode()).isEqualTo(401);
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
                    .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) request.header("Content-Type", "application/json");
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
