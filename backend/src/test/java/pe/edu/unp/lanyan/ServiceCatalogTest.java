package pe.edu.unp.lanyan;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.params.provider.EnumSource;
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
class ServiceCatalogTest {
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

    @BeforeEach
    void seed() {
        if (users.count() != 0) return;
        for (var role : RoleName.values()) {
            users.saveAndFlush(new UserAccount("Persona FICTICIA", email(role), passwords.encode(PASSWORD), roles.findAllByNameIn(Set.of(role))));
        }
    }

    private String email(RoleName role) { return role.name().toLowerCase(java.util.Locale.ROOT) + "-catalog@example.test"; }

    private String categoryId() {
        return jdbc.queryForObject("SELECT id::text FROM catalog_categories WHERE name='Soporte'", String.class);
    }

    private String body(String name, String lifecycle, Long version) {
        return "{\"name\":\"" + name + "\",\"description\":\"Descripcion ficticia\",\"categoryId\":\"" + categoryId()
                + "\",\"owner\":\"Equipo ficticio\",\"icon\":\"HEADSET\",\"lifecycleStatus\":\"" + lifecycle
                + "\",\"availabilityTarget\":99.5,\"responseTargetMinutes\":30" + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void everyPortalRoleCanReadTheInitialCatalog(RoleName role) throws Exception {
        try (var client = new Client()) {
            client.login(email(role));
            var response = client.send("GET", "/api/v1/catalog/services", null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(response.body(), "$.content[*].name")).contains("Correo institucional", "Internet y Wi-Fi");
            assertThat(client.send("GET", "/api/v1/catalog/categories", null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void anonymousVisitorsCannotReadTheCatalog() throws Exception {
        try (var client = new Client()) {
            assertThat(client.send("GET", "/api/v1/catalog/services", null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void draftsAreHiddenUntilPublishedAndRetiredServicesDisappear() throws Exception {
        try (var admin = new Client(); var user = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN));
            user.login(email(RoleName.ROLE_USER));

            var created = admin.send("POST", "/api/v1/catalog/admin/services", body("Servicio borrador", "DRAFT", null));
            assertThat(created.statusCode()).isEqualTo(201);
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(JsonPath.<String>read(created.body(), "$.code")).matches("SRV-\\d{3,}");
            assertThat(user.send("GET", "/api/v1/catalog/services/" + id, null).statusCode()).isEqualTo(404);
            assertThat(names(user)).doesNotContain("Servicio borrador");

            var published = admin.send("PUT", "/api/v1/catalog/admin/services/" + id, body("Servicio borrador", "PUBLISHED", 0L));
            assertThat(published.statusCode()).isEqualTo(200);
            assertThat(names(user)).contains("Servicio borrador");

            var retired = admin.send("PUT", "/api/v1/catalog/admin/services/" + id, body("Servicio borrador", "RETIRED", 1L));
            assertThat(retired.statusCode()).isEqualTo(200);
            assertThat(names(user)).doesNotContain("Servicio borrador");
        }
    }

    private List<String> names(Client client) throws Exception {
        return JsonPath.read(client.send("GET", "/api/v1/catalog/services", null).body(), "$.content[*].name");
    }

    @Test
    void onlyAdministratorsManageTheCatalog() throws Exception {
        for (var role : List.of(RoleName.ROLE_TECHNICIAN, RoleName.ROLE_USER, RoleName.ROLE_STUDENT)) {
            try (var client = new Client()) {
                client.login(email(role));
                assertThat(client.send("POST", "/api/v1/catalog/admin/services", body("No permitido", "DRAFT", null)).statusCode()).isEqualTo(403);
                assertThat(client.send("POST", "/api/v1/catalog/admin/categories", "{\"name\":\"No permitida\"}").statusCode()).isEqualTo(403);
                assertThat(client.send("GET", "/api/v1/catalog/admin/services", null).statusCode()).isEqualTo(403);
            }
        }
    }

    @Test
    void techniciansUpdateOperationalStatusAndEndUsersCannot() throws Exception {
        try (var tech = new Client(); var user = new Client()) {
            tech.login(email(RoleName.ROLE_TECHNICIAN));
            user.login(email(RoleName.ROLE_USER));
            var service = jdbc.queryForMap("SELECT id::text AS id, version FROM catalog_services WHERE name='Proyectores'");
            String path = "/api/v1/catalog/services/" + service.get("id") + "/operational-status";
            String json = "{\"status\":\"DEGRADED\",\"version\":" + service.get("version") + "}";

            assertThat(user.send("PUT", path, json).statusCode()).isEqualTo(403);
            var changed = tech.send("PUT", path, json);
            assertThat(changed.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(changed.body(), "$.operationalStatus")).isEqualTo("DEGRADED");
            assertThat(tech.send("PUT", path, json).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action='CATALOG_SERVICE_STATUS_CHANGED' AND target_id=? AND actor_email=?",
                    Integer.class, service.get("id"), email(RoleName.ROLE_TECHNICIAN))).isEqualTo(1);
        }
    }

    @Test
    void invalidAndDuplicateServicesAreRejected() throws Exception {
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN));
            assertThat(admin.send("POST", "/api/v1/catalog/admin/services", body("correo INSTITUCIONAL", "DRAFT", null)).statusCode()).isEqualTo(409);
            assertThat(admin.send("POST", "/api/v1/catalog/admin/services", body("Fuera de rango", "DRAFT", null).replace("99.5", "101")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/catalog/admin/services", body("Icono malo", "DRAFT", null).replace("HEADSET", "OTRO")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/catalog/admin/categories", "{\"name\":\"soporte\"}").statusCode()).isEqualTo(409);
        }
    }

    @Test
    void creatingAServiceLeavesAuditEvidence() throws Exception {
        try (var admin = new Client()) {
            admin.login(email(RoleName.ROLE_ADMIN));
            var created = admin.send("POST", "/api/v1/catalog/admin/services", body("Servicio auditado", "PUBLISHED", null));
            String id = JsonPath.read(created.body(), "$.id");
            assertThat(jdbc.queryForObject("SELECT actor_email FROM audit_events WHERE action='CATALOG_SERVICE_CREATED' AND target_id=?", String.class, id))
                    .isEqualTo(email(RoleName.ROLE_ADMIN));
        }
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

        void login(String email) throws Exception {
            csrf();
            assertThat(send("POST", "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").statusCode()).isEqualTo(200);
            csrf();
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
