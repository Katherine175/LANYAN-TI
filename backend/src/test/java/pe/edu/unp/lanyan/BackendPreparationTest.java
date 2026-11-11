package pe.edu.unp.lanyan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendPreparationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    Environment environment;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthReportsApplicationAndDatabaseUpWithoutExposingDetails() throws Exception {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.headers().firstValue("set-cookie")).isEmpty();
    }

    @Test
    void allMigrationsAreAppliedAndValidatedOnPostgreSql() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class))
                .containsExactlyInAnyOrder("flyway_schema_history", "identity_users", "identity_roles",
                        "identity_permissions", "identity_user_roles", "identity_role_permissions", "academic_student_profiles",
                        "academic_courses", "academic_course_sections", "academic_enrollments",
                        "academic_class_schedules", "academic_course_materials", "audit_events", "catalog_categories", "catalog_services", "sla_policies", "tickets", "service_requests", "access_resources", "access_requests");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT permission_name FROM identity_role_permissions WHERE role_name='ROLE_STUDENT'", String.class))
                .contains("academic:schedules:read:self", "academic:materials:read:self");
        assertThat(jdbc.queryForList("SELECT permission_name FROM identity_role_permissions WHERE role_name='ROLE_ADMIN'", String.class))
                .contains("academic:schedules:manage", "academic:materials:manage");
        assertThat(jdbc.queryForList("SELECT name FROM identity_roles", String.class))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_TECHNICIAN", "ROLE_USER", "ROLE_STUDENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_role_permissions WHERE role_name='ROLE_STUDENT' AND permission_name='academic:enrollments:read:self'", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/api/v1/users", "/api/v1/tickets", "/actuator/env", "/actuator/health/db"})
    void otherRoutesAreDeniedWithoutLoginRedirect(String path) throws Exception {
        var response = get(path);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("location")).isEmpty();
        assertThat(response.headers().firstValue("www-authenticate")).isEmpty();
    }

    private HttpResponse<String> get(String path) throws Exception {
        var port = environment.getRequiredProperty("local.server.port");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
