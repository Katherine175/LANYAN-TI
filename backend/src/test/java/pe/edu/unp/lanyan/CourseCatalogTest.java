package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "lanyan.auth.login-limit=500")
class CourseCatalogTest {
    // Cursos, secciones y personas FICTICIAS; solo viven en el PostgreSQL temporal.
    static final String PASSWORD = "Fictitious-Catalog-Test42!";
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
    @Autowired StudentProfileRepository profiles;
    @Autowired CourseRepository courses;
    @Autowired CourseSectionRepository sections;
    @Autowired ClassScheduleRepository schedules;
    @Autowired EnrollmentRepository enrollments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        if (users.count() != 0) return;
        var hash = passwords.encode(PASSWORD);
        for (var role : RoleName.values()) account(role.name().toLowerCase() + "@unp.edu.pe", role, hash);

        var english = courses.saveAndFlush(new Course("ING-B1", "Inglés intermedio FICTICIO", "Inglés", "B1", "Conversación y lectura.", CourseStatus.ACTIVE));
        var french = courses.saveAndFlush(new Course("FRA-A1", "Francés básico FICTICIO", "Francés", "A1", null, CourseStatus.ACTIVE));
        courses.saveAndFlush(new Course("ITA-A1", "Italiano retirado FICTICIO", "Italiano", "A1", null, CourseStatus.INACTIVE));

        var open = sections.saveAndFlush(new CourseSection(english, "A", "TEST-2026-I", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 30), 3, SectionStatus.OPEN));
        sections.saveAndFlush(new CourseSection(english, "B", "TEST-2026-I", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 30), 20, SectionStatus.CLOSED));
        sections.saveAndFlush(new CourseSection(english, "C", "TEST-2026-I", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 30), 20, SectionStatus.CANCELLED));

        schedules.saveAndFlush(new ClassSchedule(open, DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), ClassModality.ONLINE, null));
        schedules.saveAndFlush(new ClassSchedule(open, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0), ClassModality.IN_PERSON, "Aula 101"));
        var retired = schedules.saveAndFlush(new ClassSchedule(open, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), ClassModality.ONLINE, null));
        retired.retire();
        schedules.saveAndFlush(retired);

        var student = profiles.saveAndFlush(new StudentProfile(users.findByEmail("role_student@unp.edu.pe").orElseThrow(), "TEST-CAT-1", null));
        enrollments.saveAndFlush(new Enrollment(student, open));
        assertThat(french.getId()).isNotNull();
    }

    void account(String email, RoleName role, String hash) {
        users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(Set.of(role))));
    }

    @Test
    void studentSeesActiveCoursesWithOpenSectionsSeatsAndSchedules() throws Exception {
        try (var student = new Client()) {
            student.login("role_student@unp.edu.pe");
            var response = student.get("/api/v1/courses/catalog");
            assertThat(response.statusCode()).isEqualTo(200);
            String body = response.body();

            assertThat(JsonPath.<List<String>>read(body, "$[*].code")).containsExactly("FRA-A1", "ING-B1");
            assertThat(JsonPath.<List<Object>>read(body, "$[0].sections")).isEmpty();
            assertThat(JsonPath.<List<String>>read(body, "$[1].sections[*].code")).containsExactly("A");
            assertThat(JsonPath.<Integer>read(body, "$[1].sections[0].capacity")).isEqualTo(3);
            assertThat(JsonPath.<Integer>read(body, "$[1].sections[0].availableSeats")).isEqualTo(2);
            assertThat(JsonPath.<List<String>>read(body, "$[1].sections[0].schedules[*].dayOfWeek")).containsExactly("MONDAY", "WEDNESDAY");
            assertThat(JsonPath.<String>read(body, "$[1].sections[0].schedules[0].classroom")).isEqualTo("Aula 101");
            assertThat(body).doesNotContain("TEST-CAT-1", "role_student@unp.edu.pe");
        }
    }

    @Test
    void eveningClassesAreStoredInLimaTimeWithoutShifting() {
        // 18:00-20:00 en Lima cruza la medianoche UTC; antes se guardaba como 23:00-01:00 y la base lo rechazaba.
        var stored = jdbc.queryForList("SELECT start_time::text AS s, end_time::text AS e FROM academic_class_schedules WHERE day_of_week = 1");
        assertThat(stored).singleElement().satisfies(row -> {
            assertThat(row.get("s")).isEqualTo("18:00:00");
            assertThat(row.get("e")).isEqualTo("20:00:00");
        });
    }

    @Test
    void studentWithoutAcademicProfileCanStillBrowseTheCatalog() throws Exception {
        var hash = passwords.encode(PASSWORD);
        account("sin-perfil-" + UUID.randomUUID().toString().substring(0, 8) + "@unp.edu.pe", RoleName.ROLE_STUDENT, hash);
        var email = users.findAll().stream().map(UserAccount::getEmail).filter(e -> e.startsWith("sin-perfil-")).findFirst().orElseThrow();
        try (var student = new Client()) {
            student.login(email);
            assertThat(student.get("/api/v1/courses/catalog").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void adminCanPreviewTheCatalog() throws Exception {
        try (var admin = new Client()) {
            admin.login("role_admin@unp.edu.pe");
            assertThat(admin.get("/api/v1/courses/catalog").statusCode()).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"role_user@unp.edu.pe", "role_technician@unp.edu.pe"})
    void itPortalRolesCannotReadTheCatalog(String email) throws Exception {
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.get("/api/v1/courses/catalog").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        try (var client = new Client()) {
            assertThat(client.get("/api/v1/courses/catalog").statusCode()).isEqualTo(401);
        }
    }

    @Test
    void catalogIsReadOnly() throws Exception {
        try (var student = new Client()) {
            student.login("role_student@unp.edu.pe");
            for (var method : List.of("POST", "PUT", "DELETE")) {
                assertThat(student.send(method, "/api/v1/courses/catalog", method.equals("DELETE") ? null : "{}").statusCode())
                        .as(method).isIn(403, 405);
            }
        }
        assertThat(Map.of("courses", courses.count())).containsEntry("courses", 3L);
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
