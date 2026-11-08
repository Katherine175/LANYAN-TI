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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;

/** Un fallo técnico debe llegar con su propio código HTTP, no disfrazado de falta de permisos. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lanyan.auth.login-limit=500")
@Import(ApiErrorResponsesTest.FailingEndpoints.class)
class ApiErrorResponsesTest {
    static final String PASSWORD = "Fictitious-Test-Password42!";
    static final String ADMIN = "admin-errors@example.test";
    static final String STUDENT = "student-errors@example.test";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // @TestComponent lo deja fuera del escaneo, así no aparece en el contexto de otras pruebas.
    @TestComponent
    @RestController
    static class FailingEndpoints {
        @GetMapping("/api/v1/academic/test-failure")
        String fail() { throw new IllegalStateException("detalle-interno-secreto"); }

        @GetMapping("/api/v1/academic/test-required")
        String required(@RequestParam int value) { return "ok"; }
    }

    @Autowired Environment environment;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void seed() {
        if (users.count() != 0) return;
        users.saveAndFlush(new UserAccount("Admin FICTICIO", ADMIN, passwords.encode(PASSWORD),
                roles.findAllByNameIn(Set.of(RoleName.ROLE_ADMIN))));
        users.saveAndFlush(new UserAccount("Estudiante FICTICIO", STUDENT, passwords.encode(PASSWORD),
                roles.findAllByNameIn(Set.of(RoleName.ROLE_STUDENT))));
    }

    @Test
    void unexpectedFailureIsReportedAs500WithoutInternalDetails() throws Exception {
        try (var client = new Client(ADMIN)) {
            var response = client.send("GET", "/api/v1/academic/test-failure", null, null);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INTERNAL_ERROR");
            assertThat(response.body()).doesNotContain("detalle-interno-secreto", "IllegalStateException");
        }
    }

    @Test
    void wrongMethodIsReportedAs405() throws Exception {
        try (var client = new Client(ADMIN)) {
            var response = client.send("PUT", "/api/v1/academic/courses", "{}", "application/json");
            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("METHOD_NOT_ALLOWED");
        }
    }

    @Test
    void unsupportedBodyTypeIsReportedAs415() throws Exception {
        try (var client = new Client(ADMIN)) {
            var response = client.send("POST", "/api/v1/academic/courses", "texto", "text/plain");
            assertThat(response.statusCode()).isEqualTo(415);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        }
    }

    @Test
    void missingParameterIsReportedAs400() throws Exception {
        try (var client = new Client(ADMIN)) {
            var response = client.send("GET", "/api/v1/academic/test-required", null, null);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void permissionFailuresStillAnswer403() throws Exception {
        try (var client = new Client(STUDENT)) {
            var response = client.send("GET", "/api/v1/academic/test-failure", null, null);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).doesNotContain("detalle-interno-secreto");
        }
    }

    @Test
    void anonymousRequestsStillAnswer401() throws Exception {
        try (var client = new Client(null)) {
            assertThat(client.send("GET", "/api/v1/academic/test-failure", null, null).statusCode()).isEqualTo(401);
        }
    }

    private final class Client implements AutoCloseable {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header = "X-CSRF-TOKEN";

        Client(String email) throws Exception {
            csrf();
            if (email != null) {
                var login = send("POST", "/api/v1/auth/login",
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", "application/json");
                assertThat(login.statusCode()).isEqualTo(200);
                csrf();
            }
        }

        void csrf() throws Exception {
            var response = send("GET", "/api/v1/auth/csrf", null, null);
            token = JsonPath.read(response.body(), "$.token");
            header = JsonPath.read(response.body(), "$.headerName");
        }

        HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                    + environment.getRequiredProperty("local.server.port") + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            if (contentType != null) request.header("Content-Type", contentType);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Override public void close() { http.close(); }
    }
}
