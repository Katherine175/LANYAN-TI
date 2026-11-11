package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
class ProblemsTest {
    // Personas FICTICIAS; servicios del catálogo inicial (V7).
    static final String PASSWORD = "Fictitious-Problems-Test42!";
    static final String STUDENT = "est-prb@unp.edu.pe", OTHER = "otro-prb@unp.edu.pe", TECH = "tec-prb@unp.edu.pe";
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
        account(OTHER, RoleName.ROLE_STUDENT, hash);
        account(TECH, RoleName.ROLE_TECHNICIAN, hash);
    }

    void account(String email, RoleName role, String hash) {
        users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(Set.of(role))));
    }

    String serviceId(String name) { return jdbc.queryForObject("SELECT id::text FROM catalog_services WHERE name = ?", String.class, name); }

    String ticket(Client client, String service, String title) throws Exception {
        var body = "{\"serviceId\":\"" + serviceId(service) + "\",\"title\":\"" + title + "\"}";
        return JsonPath.read(client.send("POST", "/api/v1/tickets", body).body(), "$.id");
    }

    @Test
    void repeatedIncidentsBecomeAProblemAKnownErrorAndAreResolvedTogether() throws Exception {
        var incidents = new ArrayList<String>();
        String otherService;
        try (var student = new Client()) {
            student.login(STUDENT);
            for (int i = 1; i <= 3; i++) incidents.add(ticket(student, "Proyectores", "Proyector sin imagen " + i));
            otherService = ticket(student, "Pagos", "Pago no reflejado");
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            // Gestión proactiva: tres incidentes del mismo servicio sin problema asociado.
            var candidates = tech.get("/api/v1/problems/candidates");
            assertThat(JsonPath.<List<String>>read(candidates.body(), "$[?(@.serviceName=='Proyectores')].serviceName")).hasSize(1);

            var created = tech.send("POST", "/api/v1/problems", "{\"title\":\"Proyectores pierden la señal HDMI\",\"description\":\"Tres aulas reportan pantalla negra\","
                    + "\"serviceId\":\"" + serviceId("Proyectores") + "\",\"priority\":\"HIGH\",\"ticketIds\":[\"" + incidents.get(0) + "\",\"" + incidents.get(1) + "\"]}");
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(JsonPath.<String>read(created.body(), "$.code")).startsWith("PRB-");
            assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("INVESTIGATING");
            assertThat(JsonPath.<List<Object>>read(created.body(), "$.incidents")).hasSize(2);

            assertThat(tech.send("POST", "/api/v1/problems/" + id + "/incidents", "{\"ticketId\":\"" + incidents.get(2) + "\"}").statusCode()).isEqualTo(200);
            assertThat(code(tech.send("POST", "/api/v1/problems/" + id + "/incidents", "{\"ticketId\":\"" + incidents.get(2) + "\"}"))).isEqualTo("TICKET_ALREADY_LINKED");
            assertThat(code(tech.send("POST", "/api/v1/problems/" + id + "/incidents", "{\"ticketId\":\"" + otherService + "\"}"))).isEqualTo("SERVICE_MISMATCH");
            assertThat(JsonPath.<String>read(tech.get("/api/v1/tickets/" + incidents.get(0)).body(), "$.problemCode")).startsWith("PRB-");
            assertThat(JsonPath.<List<String>>read(tech.get("/api/v1/problems/candidates").body(), "$[*].serviceName")).doesNotContain("Proyectores");

            assertThat(code(tech.send("POST", "/api/v1/problems/" + id + "/resolve", "{\"permanentSolution\":\"Cambiar cables\",\"resolveLinkedTickets\":true}"))).isEqualTo("INVALID_TRANSITION");
            assertThat(tech.send("PUT", "/api/v1/problems/" + id + "/known-error", "{\"rootCause\":\"Cables HDMI dañados\",\"workaround\":\"\"}").statusCode()).isEqualTo(400);
            var known = tech.send("PUT", "/api/v1/problems/" + id + "/known-error", "{\"rootCause\":\"Cables HDMI dañados por el uso\",\"workaround\":\"Usar el adaptador VGA del aula\"}");
            assertThat(JsonPath.<String>read(known.body(), "$.status")).isEqualTo("KNOWN_ERROR");
            var errors = tech.get("/api/v1/problems/known-errors?serviceId=" + serviceId("Proyectores"));
            assertThat(JsonPath.<List<String>>read(errors.body(), "$[*].workaround")).containsExactly("Usar el adaptador VGA del aula");

            var resolved = tech.send("POST", "/api/v1/problems/" + id + "/resolve", "{\"permanentSolution\":\"Se reemplazaron los cables HDMI de las tres aulas\",\"resolveLinkedTickets\":true}");
            assertThat(JsonPath.<String>read(resolved.body(), "$.status")).isEqualTo("RESOLVED");
            assertThat(JsonPath.<List<String>>read(resolved.body(), "$.incidents[*].status")).containsOnly("RESOLVED");
            assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE target_type='PROBLEM' AND target_id=?", String.class, id))
                    .contains("PROBLEM_CREATED", "PROBLEM_LINKED", "PROBLEM_KNOWN_ERROR", "PROBLEM_RESOLVED");
        }
        try (var student = new Client()) {
            student.login(STUDENT);
            var comments = student.get("/api/v1/tickets/" + incidents.get(0) + "/comments");
            assertThat(JsonPath.<List<String>>read(comments.body(), "$[*].body")).anySatisfy(body -> assertThat(body).contains("PRB-", "cables HDMI"));
            assertThat(student.get("/api/v1/problems").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void commentsKeepInternalNotesFromTheRequesterAndCountAsFirstResponse() throws Exception {
        String id;
        try (var student = new Client()) {
            student.login(STUDENT);
            id = ticket(student, "Correo institucional", "No me llegan correos");
            assertThat(student.send("POST", "/api/v1/tickets/" + id + "/comments", "{\"body\":\"Desde ayer en la tarde\"}").statusCode()).isEqualTo(201);
            assertThat(student.send("POST", "/api/v1/tickets/" + id + "/comments", "{\"body\":\"Nota\",\"internal\":true}").statusCode()).isEqualTo(403);
        }
        try (var other = new Client()) {
            other.login(OTHER);
            assertThat(other.get("/api/v1/tickets/" + id + "/comments").statusCode()).isEqualTo(404);
            assertThat(other.send("POST", "/api/v1/tickets/" + id + "/comments", "{\"body\":\"Hola\"}").statusCode()).isEqualTo(404);
        }
        try (var tech = new Client()) {
            tech.login(TECH);
            assertThat(tech.send("POST", "/api/v1/tickets/" + id + "/comments", "{\"body\":\"Revisar el filtro de spam\",\"internal\":true}").statusCode()).isEqualTo(201);
            assertThat(JsonPath.<Object>read(tech.get("/api/v1/tickets/" + id).body(), "$.firstRespondedAt")).isNull();
            assertThat(tech.send("POST", "/api/v1/tickets/" + id + "/comments", "{\"body\":\"Ya revisamos tu buzón, prueba de nuevo\"}").statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(tech.get("/api/v1/tickets/" + id).body(), "$.firstResponseSla")).isEqualTo("MET");
            assertThat(JsonPath.<List<Object>>read(tech.get("/api/v1/tickets/" + id + "/comments").body(), "$")).hasSize(3);
        }
        try (var student = new Client()) {
            student.login(STUDENT);
            var visible = student.get("/api/v1/tickets/" + id + "/comments");
            assertThat(JsonPath.<List<String>>read(visible.body(), "$[*].body")).containsExactly("Desde ayer en la tarde", "Ya revisamos tu buzón, prueba de nuevo");
        }
    }

    private static String code(HttpResponse<String> response) { return JsonPath.read(response.body(), "$.code"); }

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
