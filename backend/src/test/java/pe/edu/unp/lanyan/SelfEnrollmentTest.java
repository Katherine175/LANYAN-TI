package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
class SelfEnrollmentTest {
    // Personas, cursos y códigos FICTICIOS; solo viven en el PostgreSQL temporal.
    static final String PASSWORD = "Fictitious-SelfEnroll-Test42!";
    static final String ADMIN = "admin-matricula@unp.edu.pe";
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
    @Autowired EnrollmentRepository enrollments;
    @Autowired JdbcTemplate jdbc;

    static String hash;

    @BeforeEach
    void fixture() {
        if (hash != null) return;
        hash = passwords.encode(PASSWORD);
        account(ADMIN, RoleName.ROLE_ADMIN);
        account("usuario-matricula@unp.edu.pe", RoleName.ROLE_USER);
        account("tecnico-matricula@unp.edu.pe", RoleName.ROLE_TECHNICIAN);
    }

    UserAccount account(String email, RoleName role) {
        return users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(Set.of(role))));
    }

    /** Estudiante nuevo con perfil, para que cada prueba sea independiente. */
    String student(boolean withProfile) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var account = account("est-" + suffix + "@unp.edu.pe", RoleName.ROLE_STUDENT);
        if (withProfile) profiles.saveAndFlush(new StudentProfile(account, "FIC-" + suffix, null));
        return account.getEmail();
    }

    Course course(CourseStatus status) {
        var code = "FIC-" + UUID.randomUUID().toString().substring(0, 8);
        return courses.saveAndFlush(new Course(code, "Curso FICTICIO", "Idioma FICTICIO", "A1", null, status));
    }

    UUID section(Course course, String code, int capacity, SectionStatus status) {
        return sections.saveAndFlush(new CourseSection(course, code, "TEST-2026", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 6, 30), capacity, status)).getId();
    }

    String body(UUID sectionId) { return "{\"sectionId\":\"" + sectionId + "\"}"; }

    @Test
    void studentEnrollsFromTheCatalogAndSeesTheCourse() throws Exception {
        var course = course(CourseStatus.ACTIVE);
        var sectionId = section(course, "A", 10, SectionStatus.OPEN);
        try (var client = new Client()) {
            client.login(student(true));
            var created = client.send("POST", "/api/v1/students/me/enrollments", body(sectionId));
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("ENROLLED");

            var mine = client.get("/api/v1/students/me/courses");
            assertThat(JsonPath.<List<String>>read(mine.body(), "$.content[*].course.code")).containsExactly(course.getCode());

            var catalog = client.get("/api/v1/courses/catalog");
            assertThat(JsonPath.<List<Integer>>read(catalog.body(), "$[?(@.code=='" + course.getCode() + "')].sections[0].availableSeats")).containsExactly(9);

            String id = JsonPath.read(created.body(), "$.id");
            var detail = jdbc.queryForObject("SELECT detail FROM audit_events WHERE action='ENROLLMENT_CREATED' AND target_id=?", String.class, id);
            assertThat(detail).contains("curso=" + course.getCode(), "origen=catálogo");
        }
    }

    @Test
    void studentCannotTakeTwoSeatsInTheSameCourse() throws Exception {
        var course = course(CourseStatus.ACTIVE);
        var first = section(course, "A", 10, SectionStatus.OPEN);
        var second = section(course, "B", 10, SectionStatus.OPEN);
        try (var client = new Client()) {
            client.login(student(true));
            assertThat(client.send("POST", "/api/v1/students/me/enrollments", body(first)).statusCode()).isEqualTo(201);
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(first)))).isEqualTo("ENROLLMENT_EXISTS");
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(second)))).isEqualTo("COURSE_ALREADY_ENROLLED");
        }
    }

    @Test
    void fullClosedAndInactiveOfferingsAreRejected() throws Exception {
        var active = course(CourseStatus.ACTIVE);
        var full = section(active, "LLENA", 1, SectionStatus.OPEN);
        var closed = section(active, "CERRADA", 10, SectionStatus.CLOSED);
        var inactive = section(course(CourseStatus.INACTIVE), "A", 10, SectionStatus.OPEN);
        try (var first = new Client()) {
            first.login(student(true));
            assertThat(first.send("POST", "/api/v1/students/me/enrollments", body(full)).statusCode()).isEqualTo(201);
        }
        try (var client = new Client()) {
            client.login(student(true));
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(full)))).isEqualTo("SECTION_FULL");
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(closed)))).isEqualTo("SECTION_NOT_OPEN");
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(inactive)))).isEqualTo("COURSE_INACTIVE");
            assertThat(code(client.send("POST", "/api/v1/students/me/enrollments", body(UUID.randomUUID())))).isEqualTo("SECTION_NOT_FOUND");
        }
        assertThat(enrollments.countBySection_IdAndStatus(full, EnrollmentStatus.ENROLLED)).isEqualTo(1);
    }

    @Test
    void studentWithoutProfileIsToldToContactAdministration() throws Exception {
        var sectionId = section(course(CourseStatus.ACTIVE), "A", 10, SectionStatus.OPEN);
        try (var client = new Client()) {
            client.login(student(false));
            var response = client.send("POST", "/api/v1/students/me/enrollments", body(sectionId));
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(code(response)).isEqualTo("STUDENT_PROFILE_NOT_FOUND");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN, "usuario-matricula@unp.edu.pe", "tecnico-matricula@unp.edu.pe"})
    void onlyStudentsCanSelfEnroll(String email) throws Exception {
        var sectionId = section(course(CourseStatus.ACTIVE), "A", 10, SectionStatus.OPEN);
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.send("POST", "/api/v1/students/me/enrollments", body(sectionId)).statusCode()).isEqualTo(403);
        }
        assertThat(enrollments.countBySection_IdAndStatus(sectionId, EnrollmentStatus.ENROLLED)).isZero();
    }

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        var sectionId = section(course(CourseStatus.ACTIVE), "A", 10, SectionStatus.OPEN);
        try (var client = new Client()) {
            client.csrf();
            assertThat(client.send("POST", "/api/v1/students/me/enrollments", body(sectionId)).statusCode()).isEqualTo(401);
        }
        try (var client = new Client()) {
            client.login(student(true));
            client.token = null;
            assertThat(client.send("POST", "/api/v1/students/me/enrollments", body(sectionId)).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void adminRegistersTheStudentCodeAndThenTheStudentCanEnroll() throws Exception {
        var email = student(false);
        var userId = users.findByEmail(email).orElseThrow().getId();
        var sectionId = section(course(CourseStatus.ACTIVE), "A", 10, SectionStatus.OPEN);
        String code = "fic-" + UUID.randomUUID().toString().substring(0, 6);
        try (var admin = new Client()) {
            admin.login(ADMIN);
            assertThat(admin.get("/api/v1/academic/students/" + userId + "/profile").statusCode()).isEqualTo(404);
            var saved = admin.send("PUT", "/api/v1/academic/students/" + userId + "/profile", "{\"studentCode\":\"" + code + "\"}");
            assertThat(saved.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(saved.body(), "$.studentCode")).isEqualTo(code.toUpperCase());
            var renamed = admin.send("PUT", "/api/v1/academic/students/" + userId + "/profile", "{\"studentCode\":\"" + code + "-2\"}");
            assertThat(JsonPath.<String>read(renamed.body(), "$.studentCode")).isEqualTo(code.toUpperCase() + "-2");
            assertThat(profiles.findByUser_Id(userId)).isPresent();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_student_profiles WHERE user_id=?", Long.class, userId)).isEqualTo(1);
        }
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.send("POST", "/api/v1/students/me/enrollments", body(sectionId)).statusCode()).isEqualTo(201);
        }
    }

    @Test
    void studentCodesAreUniqueAndOnlyForStudentAccounts() throws Exception {
        var taken = profiles.findByUser_Id(users.findByEmail(student(true)).orElseThrow().getId()).orElseThrow().getStudentCode();
        var otherId = users.findByEmail(student(false)).orElseThrow().getId();
        var userId = users.findByEmail("usuario-matricula@unp.edu.pe").orElseThrow().getId();
        try (var admin = new Client()) {
            admin.login(ADMIN);
            assertThat(code(admin.send("PUT", "/api/v1/academic/students/" + otherId + "/profile", "{\"studentCode\":\"" + taken.toLowerCase() + "\"}"))).isEqualTo("STUDENT_CODE_EXISTS");
            assertThat(code(admin.send("PUT", "/api/v1/academic/students/" + userId + "/profile", "{\"studentCode\":\"FIC-NO\"}"))).isEqualTo("NOT_A_STUDENT");
            assertThat(admin.send("PUT", "/api/v1/academic/students/" + otherId + "/profile", "{\"studentCode\":\" \"}").statusCode()).isEqualTo(400);
            assertThat(code(admin.send("PUT", "/api/v1/academic/students/" + UUID.randomUUID() + "/profile", "{\"studentCode\":\"FIC-X\"}"))).isEqualTo("USER_NOT_FOUND");
        }
        try (var student = new Client()) {
            student.login(student(true));
            assertThat(student.send("PUT", "/api/v1/academic/students/" + otherId + "/profile", "{\"studentCode\":\"FIC-HACK\"}").statusCode()).isEqualTo(403);
        }
        assertThat(profiles.findByUser_Id(otherId)).isEmpty();
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
                    .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) request.header("Content-Type", "application/json");
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
