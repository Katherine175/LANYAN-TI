package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.application.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import pe.edu.unp.lanyan.identity.domain.*;
import pe.edu.unp.lanyan.identity.infrastructure.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "lanyan.auth.login-limit=500")
class AcademicEnrollmentSecurityTest {
    // Todos los datos académicos de esta clase son FICTICIOS y solo viven en Testcontainers.
    static final String PASSWORD = "Fictitious-Enrollment-Test42!";
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
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseRepository courses;
    @Autowired CourseSectionRepository sections;
    @Autowired EnrollmentService enrollmentService;
    @Autowired CourseService courseService;
    @Autowired CourseSectionService sectionService;
    @Autowired AccountDetailsService accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        if (users.count() != 0) return;
        var hash = passwords.encode(PASSWORD);
        seedAccount("admin@example.test", Set.of(RoleName.ROLE_ADMIN), hash);
        seedAccount("user@example.test", Set.of(RoleName.ROLE_USER), hash);
        seedAccount("technician@example.test", Set.of(RoleName.ROLE_TECHNICIAN), hash);
        seedAccount("missing-profile@example.test", Set.of(RoleName.ROLE_STUDENT), hash);
    }

    UserAccount seedAccount(String email, Set<RoleName> names, String hash) {
        return users.saveAndFlush(new UserAccount("Persona FICTICIA", email, hash, roles.findAllByNameIn(names)));
    }

    StudentProfile student() {
        var suffix = UUID.randomUUID().toString().substring(0, 12);
        var hash = users.findByEmail("admin@example.test").orElseThrow().getPasswordHash();
        var account = seedAccount("student-" + suffix + "@example.test", Set.of(RoleName.ROLE_STUDENT), hash);
        return profiles.saveAndFlush(new StudentProfile(account, "TEST-" + suffix, null));
    }

    String courseBody(String code, String status) {
        return "{\"code\":\"" + code + "\",\"name\":\"Curso FICTICIO\",\"language\":\"Idioma FICTICIO\",\"level\":\"Nivel FICTICIO\",\"status\":\"" + status + "\"}";
    }

    UUID createCourse(Client admin, String status) throws Exception {
        var result = admin.send("POST", "/api/v1/academic/courses", courseBody("TEST-" + UUID.randomUUID().toString().substring(0, 12), status));
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(admin.get(result.headers().firstValue("location").orElseThrow()).statusCode()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(result.body(), "$.id"));
    }

    String sectionBody(String code, String period, int capacity, String status) {
        return "{\"code\":\"" + code + "\",\"academicPeriod\":\"" + period + "\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\",\"capacity\":" + capacity + ",\"status\":\"" + status + "\"}";
    }

    UUID createSection(Client admin, UUID courseId, int capacity, String status) throws Exception {
        var result = admin.send("POST", "/api/v1/academic/courses/" + courseId + "/sections", sectionBody("A", "TEST-2026", capacity, status));
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(admin.get(result.headers().firstValue("location").orElseThrow()).statusCode()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(result.body(), "$.id"));
    }

    String enrollmentBody(UUID studentId, UUID sectionId) {
        return "{\"studentProfileId\":\"" + studentId + "\",\"sectionId\":\"" + sectionId + "\"}";
    }

    HttpResponse<String> enroll(Client admin, StudentProfile student, UUID sectionId) throws Exception {
        return admin.send("POST", "/api/v1/academic/enrollments", enrollmentBody(student.getId(), sectionId));
    }

    @Test
    void adminRegistersCourseSectionAndEnrollmentAndCanFilterItsRecords() throws Exception {
        var student = student();
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            var courseId = createCourse(admin, "ACTIVE");
            var sectionId = createSection(admin, courseId, 2, "OPEN");
            var response = enroll(admin, student, sectionId);
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(JsonPath.<String>read(response.body(), "$.studentProfileId")).isEqualTo(student.getId().toString());
            assertThat(JsonPath.<String>read(response.body(), "$.courseId")).isEqualTo(courseId.toString());
            assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("ENROLLED");
            assertThat(admin.get(response.headers().firstValue("location").orElseThrow()).statusCode()).isEqualTo(200);
            assertThat(admin.get("/api/v1/academic/courses").statusCode()).isEqualTo(200);
            var byStudent = admin.get("/api/v1/academic/enrollments?studentProfileId=" + student.getId());
            var bySection = admin.get("/api/v1/academic/enrollments?sectionId=" + sectionId);
            var both = admin.get("/api/v1/academic/enrollments?studentProfileId=" + student.getId() + "&sectionId=" + sectionId);
            for (var result : List.of(byStudent, bySection, both)) {
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(JsonPath.<Integer>read(result.body(), "$.totalElements")).isEqualTo(1);
            }
            assertThat(admin.get("/api/v1/academic/enrollments").statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(admin.get("/api/v1/academic/courses/" + courseId + "/sections").body(), "$.totalElements")).isEqualTo(1);
        }
    }

    @Test
    void studentsReadOnlyTheirOwnCoursesEvenWhenAnotherIdentityIsSupplied() throws Exception {
        var a = student();
        var b = student();
        try (var admin = new Client(); var clientA = new Client(); var clientB = new Client()) {
            admin.login("admin@example.test");
            var courseA = createCourse(admin, "ACTIVE");
            var courseB = createCourse(admin, "ACTIVE");
            var sectionA = createSection(admin, courseA, 5, "OPEN");
            var sectionB = createSection(admin, courseB, 5, "OPEN");
            var enrollmentA = enroll(admin, a, sectionA);
            var enrollmentB = enroll(admin, b, sectionB);
            assertThat(enrollmentA.statusCode()).isEqualTo(201);
            assertThat(enrollmentB.statusCode()).isEqualTo(201);
            clientA.login(a.getUser().getEmail());
            clientB.login(b.getUser().getEmail());
            var resultA = clientA.get("/api/v1/students/me/courses?studentProfileId=" + b.getId() + "&userId=" + b.getUser().getId());
            var resultB = clientB.get("/api/v1/students/me/courses");
            assertThat(resultA.statusCode()).isEqualTo(200);
            assertThat(resultB.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(resultA.body(), "$.content[0].course.id")).isEqualTo(courseA.toString());
            assertThat(JsonPath.<String>read(resultA.body(), "$.content[0].section.courseId")).isEqualTo(courseA.toString());
            assertThat(JsonPath.<String>read(resultB.body(), "$.content[0].course.id")).isEqualTo(courseB.toString());
            assertThat(resultA.body()).doesNotContain(courseB.toString(), sectionB.toString(), b.getId().toString(), b.getUser().getEmail(), "password", "studentProfileId");
            assertThat(resultB.body()).doesNotContain(courseA.toString(), a.getUser().getEmail());
            assertThat(clientA.get("/api/v1/students/" + b.getUser().getId() + "/courses").statusCode()).isEqualTo(403);
            assertThat(clientA.get("/api/v1/academic/enrollments/" + JsonPath.<String>read(enrollmentB.body(), "$.id")).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void noEnrollmentsReturnsAnEmptyPageAndNoProfileReturns404() throws Exception {
        var student = student();
        try (var empty = new Client(); var missing = new Client()) {
            empty.login(student.getUser().getEmail());
            var response = empty.get("/api/v1/students/me/courses");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(response.body(), "$.totalElements")).isZero();
            assertThat(JsonPath.<List<Object>>read(response.body(), "$.content")).isEmpty();
            missing.login("missing-profile@example.test");
            var error = missing.get("/api/v1/students/me/courses");
            assertThat(error.statusCode()).isEqualTo(404);
            assertThat(error.body()).contains("STUDENT_PROFILE_NOT_FOUND");
        }
    }

    @Test
    void paginationIsBoundedAndContainsOnlyTheStudentsRecords() throws Exception {
        var student = student();
        try (var admin = new Client(); var reader = new Client()) {
            admin.login("admin@example.test");
            for (int index = 0; index < 3; index++) {
                var courseId = createCourse(admin, "ACTIVE");
                assertThat(enroll(admin, student, createSection(admin, courseId, 5, "OPEN")).statusCode()).isEqualTo(201);
            }
            reader.login(student.getUser().getEmail());
            var first = reader.get("/api/v1/students/me/courses?page=0&size=1");
            var second = reader.get("/api/v1/students/me/courses?page=1&size=1");
            assertThat(JsonPath.<Integer>read(first.body(), "$.totalElements")).isEqualTo(3);
            assertThat(JsonPath.<String>read(first.body(), "$.content[0].enrollmentId"))
                    .isNotEqualTo(JsonPath.<String>read(second.body(), "$.content[0].enrollmentId"));
            assertThat(reader.get("/api/v1/students/me/courses?size=101").statusCode()).isEqualTo(400);
            assertThat(reader.get("/api/v1/students/me/courses?page=-1").statusCode()).isEqualTo(400);
            assertThat(admin.get("/api/v1/academic/courses?size=0").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void anonymousRequestsAndMissingCsrfCannotPerformAcademicOperations() throws Exception {
        try (var anonymous = new Client(); var admin = new Client()) {
            assertThat(anonymous.get("/api/v1/students/me/courses").statusCode()).isEqualTo(401);
            assertThat(anonymous.get("/api/v1/academic/courses").statusCode()).isEqualTo(401);
            anonymous.csrf();
            assertThat(anonymous.send("POST", "/api/v1/academic/courses", courseBody("ANON-TEST", "ACTIVE")).statusCode()).isEqualTo(401);
            admin.login("admin@example.test");
            admin.token = null;
            assertThat(admin.send("POST", "/api/v1/academic/courses", courseBody("NO-CSRF-TEST", "ACTIVE")).statusCode()).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ROLE_USER", "ROLE_TECHNICIAN", "ROLE_STUDENT"})
    void nonAdminsCannotUseAcademicAdministration(RoleName role) throws Exception {
        String email = switch (role) {
            case ROLE_TECHNICIAN -> "technician@example.test";
            case ROLE_STUDENT -> "missing-profile@example.test";
            default -> "user@example.test";
        };
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.get("/api/v1/academic/courses").statusCode()).isEqualTo(403);
            assertThat(client.get("/api/v1/academic/enrollments").statusCode()).isEqualTo(403);
            assertThat(client.send("POST", "/api/v1/academic/courses", courseBody("FORBIDDEN", "ACTIVE")).statusCode()).isEqualTo(403);
            assertThat(client.send("POST", "/api/v1/academic/courses/" + UUID.randomUUID() + "/sections", sectionBody("A", "TEST", 1, "OPEN")).statusCode()).isEqualTo(403);
            assertThat(client.send("POST", "/api/v1/academic/enrollments", enrollmentBody(UUID.randomUUID(), UUID.randomUUID())).statusCode()).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin@example.test", "user@example.test", "technician@example.test"})
    void nonStudentsCannotReadTheStudentCourseEndpoint(String email) throws Exception {
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.get("/api/v1/students/me/courses").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void roleAloneDoesNotGrantAdministrativePermissionsAndServiceOwnershipCannotBeBypassed() {
        var student = student();
        var principal = accounts.refresh(student.getUser().getId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> enrollmentService.myCourses(UUID.randomUUID(), 0, 20)).isInstanceOf(AccessDeniedException.class);
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    Set.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("portal:academic:access"))));
            assertThatThrownBy(() -> enrollmentService.myCourses(student.getUser().getId(), 0, 20)).isInstanceOf(AccessDeniedException.class);
            var admin = users.findByEmail("admin@example.test").orElseThrow();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(accounts.refresh(admin.getId()), null,
                    Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThatThrownBy(() -> courseService.list(0, 20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> courseService.create(new CreateCourseRequest("TEST", "Curso FICTICIO", "Idioma FICTICIO", null, null, null))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> sectionService.create(UUID.randomUUID(), null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> enrollmentService.create(new CreateEnrollmentRequest(student.getId(), UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void duplicateCourseAndSectionKeysAreRejectedAndSectionCodesCanRepeatInAnotherPeriod() throws Exception {
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            String code = "TEST-" + UUID.randomUUID().toString().substring(0, 12);
            var first = admin.send("POST", "/api/v1/academic/courses", courseBody(code, "ACTIVE"));
            assertThat(first.statusCode()).isEqualTo(201);
            assertThat(admin.send("POST", "/api/v1/academic/courses", courseBody(code.toLowerCase(java.util.Locale.ROOT), "ACTIVE")).statusCode()).isEqualTo(409);
            var courseId = UUID.fromString(JsonPath.read(first.body(), "$.id"));
            createSection(admin, courseId, 3, "OPEN");
            assertThat(admin.send("POST", "/api/v1/academic/courses/" + courseId + "/sections", sectionBody("a", "test-2026", 3, "OPEN")).statusCode()).isEqualTo(409);
            assertThat(admin.send("POST", "/api/v1/academic/courses/" + courseId + "/sections", sectionBody("A", "TEST-2027", 3, "OPEN")).statusCode()).isEqualTo(201);
            assertThat(JsonPath.<Integer>read(admin.get("/api/v1/academic/courses/" + courseId + "/sections").body(), "$.totalElements")).isEqualTo(2);
        }
    }

    @Test
    void duplicateEnrollmentIsRejectedAndAFilledSectionDoesNotOverbook() throws Exception {
        var a = student();
        var b = student();
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            var sectionId = createSection(admin, createCourse(admin, "ACTIVE"), 1, "OPEN");
            assertThat(enroll(admin, a, sectionId).statusCode()).isEqualTo(201);
            var duplicate = enroll(admin, a, sectionId);
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(duplicate.body()).contains("ENROLLMENT_EXISTS");
            var full = enroll(admin, b, sectionId);
            assertThat(full.statusCode()).isEqualTo(409);
            assertThat(full.body()).contains("SECTION_FULL");
            assertThat(enrollments.countBySection_IdAndStatus(sectionId, EnrollmentStatus.ENROLLED)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE,OPEN,COURSE_INACTIVE", "ACTIVE,CLOSED,SECTION_NOT_OPEN", "ACTIVE,CANCELLED,SECTION_NOT_OPEN"})
    void courseAndSectionStatesAreCheckedBeforeEnrollment(String courseStatus, String sectionStatus, String error) throws Exception {
        var student = student();
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            var sectionId = createSection(admin, createCourse(admin, courseStatus), 3, sectionStatus);
            var response = enroll(admin, student, sectionId);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).contains(error);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void profileAloneDoesNotMakeAnAccountEligibleForEnrollment(boolean active) throws Exception {
        var target = student();
        if (!active) jdbc.update("UPDATE identity_users SET active=false WHERE id=?", target.getUser().getId());
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            if (active) assertThat(admin.send("PUT", "/api/v1/users/" + target.getUser().getId() + "/roles", "{\"roles\":[\"ROLE_USER\"]}").statusCode()).isEqualTo(200);
            var sectionId = createSection(admin, createCourse(admin, "ACTIVE"), 3, "OPEN");
            var response = enroll(admin, target, sectionId);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).contains("STUDENT_NOT_ELIGIBLE");
        }
    }

    @Test
    void missingRelationsInvalidFieldsAndMalformedRequestsAreRejected() throws Exception {
        var student = student();
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            assertThat(admin.get("/api/v1/academic/courses/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(admin.get("/api/v1/academic/sections/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(admin.get("/api/v1/academic/enrollments/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(admin.send("POST", "/api/v1/academic/courses/" + UUID.randomUUID() + "/sections", sectionBody("A", "TEST", 1, "OPEN")).statusCode()).isEqualTo(404);
            var courseId = createCourse(admin, "ACTIVE");
            var sectionId = createSection(admin, courseId, 3, "OPEN");
            assertThat(admin.send("POST", "/api/v1/academic/enrollments", enrollmentBody(UUID.randomUUID(), sectionId)).statusCode()).isEqualTo(404);
            assertThat(enroll(admin, student, UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(admin.send("POST", "/api/v1/academic/enrollments", "{}").statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/academic/courses", "{}").statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/academic/courses", courseBody("BAD-STATE", "UNKNOWN")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/academic/courses/" + courseId + "/sections", sectionBody("B", "TEST", 0, "OPEN")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/academic/courses/" + courseId + "/sections", sectionBody("B", "TEST", 1, "OPEN").replace("2026-12-31", "2025-01-01")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST", "/api/v1/academic/courses", "not-json").statusCode()).isEqualTo(400);
        }
    }

    @Test
    void revokingStudentAccessDoesNotRemoveItsExistingEnrollments() throws Exception {
        var student = student();
        try (var admin = new Client(); var reader = new Client()) {
            admin.login("admin@example.test");
            var sectionId = createSection(admin, createCourse(admin, "ACTIVE"), 3, "OPEN");
            assertThat(enroll(admin, student, sectionId).statusCode()).isEqualTo(201);
            reader.login(student.getUser().getEmail());
            assertThat(reader.get("/api/v1/students/me/courses").statusCode()).isEqualTo(200);
            assertThat(admin.send("PUT", "/api/v1/users/" + student.getUser().getId() + "/roles", "{\"roles\":[\"ROLE_USER\"]}").statusCode()).isEqualTo(200);
            assertThat(reader.get("/api/v1/students/me/courses").statusCode()).isEqualTo(403);
            assertThat(enrollments.existsByStudent_IdAndSection_Id(student.getId(), sectionId)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"last-seat", "same-student"})
    void concurrentRequestsCannotOverbookOrDuplicateAnEnrollment(String scenario) throws Exception {
        var a = student();
        var b = scenario.equals("same-student") ? a : student();
        try (var firstAdmin = new Client(); var secondAdmin = new Client(); var executor = Executors.newFixedThreadPool(2)) {
            firstAdmin.login("admin@example.test");
            secondAdmin.login("admin@example.test");
            var sectionId = createSection(firstAdmin, createCourse(firstAdmin, "ACTIVE"), scenario.equals("same-student") ? 5 : 1, "OPEN");
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return enroll(firstAdmin, a, sectionId).statusCode(); });
            var second = executor.submit(() -> { start.await(); return enroll(secondAdmin, b, sectionId).statusCode(); });
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(enrollments.countBySection_IdAndStatus(sectionId, EnrollmentStatus.ENROLLED)).isEqualTo(1);
        }
    }

    @Test
    void databaseConstraintsProtectRelationsStatesAndUniquenessOutsideTheApi() throws Exception {
        var a = student();
        var b = student();
        try (var admin = new Client()) {
            admin.login("admin@example.test");
            var courseId = createCourse(admin, "ACTIVE");
            var sectionId = createSection(admin, courseId, 3, "OPEN");
            assertThat(enroll(admin, a, sectionId).statusCode()).isEqualTo(201);
            String insert = "INSERT INTO academic_enrollments(id,student_profile_id,section_id,status) VALUES (?,?,?,?)";
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), a.getId(), sectionId, "ENROLLED")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), sectionId, "ENROLLED")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), b.getId(), UUID.randomUUID(), "ENROLLED")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), b.getId(), sectionId, "UNKNOWN")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE academic_course_sections SET capacity=0 WHERE id=?", sectionId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE academic_course_sections SET end_date=start_date-1 WHERE id=?", sectionId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE academic_course_sections SET course_id=? WHERE id=?", UUID.randomUUID(), sectionId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(enrollments.countBySection_IdAndStatus(sectionId, EnrollmentStatus.ENROLLED)).isEqualTo(1);
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
