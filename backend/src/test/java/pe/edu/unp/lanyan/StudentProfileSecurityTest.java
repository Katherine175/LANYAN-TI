package pe.edu.unp.lanyan;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import pe.edu.unp.lanyan.academic.application.StudentProfileService;
import pe.edu.unp.lanyan.academic.domain.StudentProfile;
import pe.edu.unp.lanyan.academic.infrastructure.StudentProfileRepository;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.AccountDetailsService;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lanyan.auth.login-limit=100")
class StudentProfileSecurityTest {
    // Datos exclusivamente ficticios y locales al contenedor de esta clase.
    static final String PASSWORD = "Fictitious-Academic-Test42!";
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
    @Autowired StudentProfileRepository profiles;
    @Autowired StudentProfileService service;
    @Autowired AccountDetailsService accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedFictitiousAccounts() {
        if (users.count() != 0) return;
        String hash = passwords.encode(PASSWORD);
        var studentA = seed("a@example.test", Set.of(RoleName.ROLE_STUDENT), hash);
        var studentB = seed("b@example.test", Set.of(RoleName.ROLE_STUDENT), hash);
        profiles.saveAndFlush(new StudentProfile(studentA, "TEST-A", LocalDate.of(2026, 1, 15)));
        profiles.saveAndFlush(new StudentProfile(studentB, "TEST-B", LocalDate.of(2026, 2, 15)));
        seed("missing@example.test", Set.of(RoleName.ROLE_STUDENT), hash);
        seed("admin@example.test", Set.of(RoleName.ROLE_ADMIN), hash);
        seed("user@example.test", Set.of(RoleName.ROLE_USER), hash);
        seed("technician@example.test", Set.of(RoleName.ROLE_TECHNICIAN), hash);
        var multi = seed("multi@example.test", Set.of(RoleName.ROLE_ADMIN, RoleName.ROLE_STUDENT), hash);
        profiles.saveAndFlush(new StudentProfile(multi, "TEST-MULTI", null));
    }

    private UserAccount seed(String email, Set<RoleName> names, String hash) {
        return users.saveAndFlush(new UserAccount("Persona FICTICIA " + email, email, hash, roles.findAllByNameIn(names)));
    }

    @Test
    void anonymousCannotReadAStudentProfile() throws Exception {
        try (var client = new Client()) {
            var response = client.get("/api/v1/students/me");
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("AUTHENTICATION_REQUIRED").doesNotContain("TEST-A", "TEST-B");
        }
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ROLE_ADMIN", "ROLE_USER", "ROLE_TECHNICIAN"})
    void aNonStudentCannotReadTheAcademicProfile(RoleName role) throws Exception {
        String email = switch (role) {
            case ROLE_ADMIN -> "admin@example.test";
            case ROLE_TECHNICIAN -> "technician@example.test";
            default -> "user@example.test";
        };
        try (var client = new Client()) {
            client.login(email);
            assertThat(client.get("/api/v1/students/me").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void twoStudentsCanReadOnlyTheirOwnProfileEvenWithInjectedQueryIdentifiers() throws Exception {
        var studentA = users.findByEmail("a@example.test").orElseThrow();
        var studentB = users.findByEmail("b@example.test").orElseThrow();
        try (var clientA = new Client(); var clientB = new Client()) {
            clientA.login(studentA.getEmail());
            clientB.login(studentB.getEmail());
            var a = clientA.get("/api/v1/students/me?userId=" + studentB.getId() + "&studentId=" + studentB.getId());
            var b = clientB.get("/api/v1/students/me");
            assertThat(a.statusCode()).isEqualTo(200);
            assertThat(b.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(a.body(), "$.userId")).isEqualTo(studentA.getId().toString());
            assertThat(JsonPath.<String>read(b.body(), "$.userId")).isEqualTo(studentB.getId().toString());
            assertThat(JsonPath.<String>read(a.body(), "$.studentCode")).isEqualTo("TEST-A");
            assertThat(JsonPath.<String>read(a.body(), "$.admissionDate")).isEqualTo("2026-01-15");
            assertThat(JsonPath.<String>read(a.body(), "$.fullName")).isEqualTo(studentA.getFullName());
            assertThat(a.body()).doesNotContain("TEST-B", studentB.getEmail(), "password", "passwordHash", "roles", "permissions");
            assertThat(b.body()).doesNotContain("TEST-A", studentA.getEmail(), "password");
            assertThat(clientA.get("/api/v1/students/" + studentB.getId()).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void aMissingProfileReturns404AndIsNotAutomaticallyCreated() throws Exception {
        long count = profiles.count();
        try (var client = new Client()) {
            client.login("missing@example.test");
            var response = client.get("/api/v1/students/me");
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("STUDENT_PROFILE_NOT_FOUND");
            assertThat(response.body()).doesNotContain("TEST-A", "TEST-B", "a@example.test");
        }
        assertThat(profiles.count()).isEqualTo(count);
    }

    @Test
    void multipleRolesStillReturnOnlyTheAuthenticatedStudentsProfile() throws Exception {
        try (var client = new Client()) {
            client.login("multi@example.test");
            var response = client.get("/api/v1/students/me");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.body(), "$.studentCode")).isEqualTo("TEST-MULTI");
            assertThat(JsonPath.<Object>read(response.body(), "$.admissionDate")).isNull();
        }
    }

    @Test
    void ownershipIsAlsoEnforcedWhenCallingTheServiceDirectly() {
        var owner = users.findByEmail("a@example.test").orElseThrow();
        var other = users.findByEmail("b@example.test").orElseThrow();
        var principal = accounts.refresh(owner.getId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            assertThat(service.getMyProfile(owner.getId()).studentCode()).isEqualTo("TEST-A");
            assertThatThrownBy(() -> service.getMyProfile(other.getId())).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void theStudentRoleAloneDoesNotReplaceTheAcademicPermission() {
        var owner = users.findByEmail("a@example.test").orElseThrow();
        var principal = accounts.refresh(owner.getId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                Set.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> service.getMyProfile(owner.getId())).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void revokingTheStudentRoleDeniesAnExistingSessionWithoutDeletingTheProfile() throws Exception {
        var target = seed("revoke-" + UUID.randomUUID() + "@example.test", Set.of(RoleName.ROLE_STUDENT, RoleName.ROLE_USER), passwords.encode(PASSWORD));
        profiles.saveAndFlush(new StudentProfile(target, "TEST-" + UUID.randomUUID().toString().substring(0, 10), null));
        try (var student = new Client(); var admin = new Client()) {
            student.login(target.getEmail());
            assertThat(student.get("/api/v1/students/me").statusCode()).isEqualTo(200);
            admin.login("admin@example.test");
            assertThat(admin.send("PUT", "/api/v1/users/" + target.getId() + "/roles", "{\"roles\":[\"ROLE_USER\"]}").statusCode()).isEqualTo(200);
            assertThat(student.get("/api/v1/students/me").statusCode()).isEqualTo(403);
        }
        assertThat(profiles.findByUser_Id(target.getId())).isPresent();
    }

    @Test
    void disablingTheAccountInvalidatesAcademicAccessWithoutDeletingTheProfile() throws Exception {
        var target = seed("disable-" + UUID.randomUUID() + "@example.test", Set.of(RoleName.ROLE_STUDENT), passwords.encode(PASSWORD));
        profiles.saveAndFlush(new StudentProfile(target, "TEST-" + UUID.randomUUID().toString().substring(0, 10), null));
        try (var student = new Client()) {
            student.login(target.getEmail());
            assertThat(student.get("/api/v1/students/me").statusCode()).isEqualTo(200);
            jdbc.update("UPDATE identity_users SET active=false WHERE id=?", target.getId());
            assertThat(student.get("/api/v1/students/me").statusCode()).isEqualTo(401);
        }
        assertThat(profiles.findByUser_Id(target.getId())).isPresent();
    }

    @Test
    void noProfileWritesOrOtherStudentEndpointsAreExposed() throws Exception {
        long count = profiles.count();
        try (var client = new Client()) {
            client.login("a@example.test");
            assertThat(client.send("POST", "/api/v1/students", "{}").statusCode()).isEqualTo(403);
            assertThat(client.send("PUT", "/api/v1/students/me", "{}").statusCode()).isEqualTo(403);
            assertThat(client.get("/api/v1/students").statusCode()).isEqualTo(403);
        }
        assertThat(profiles.count()).isEqualTo(count);
    }

    @Test
    void databaseRejectsDuplicateProfilesDuplicateCodesAndInvalidReferences() {
        var owner = users.findByEmail("a@example.test").orElseThrow();
        var available = users.findByEmail("user@example.test").orElseThrow();
        long count = profiles.count();
        assertThatThrownBy(() -> profiles.saveAndFlush(new StudentProfile(owner, "TEST-DUP-USER", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> profiles.saveAndFlush(new StudentProfile(available, "TEST-A", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic_student_profiles(id,user_id,student_code) VALUES (?,?,?)",
                UUID.randomUUID(), UUID.randomUUID(), "TEST-MISSING-USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic_student_profiles(id,user_id,student_code) VALUES (?,?,?)",
                UUID.randomUUID(), available.getId(), ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic_student_profiles(id,user_id,student_code) VALUES (?,?,?)",
                UUID.randomUUID(), available.getId(), "lowercase"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(profiles.count()).isEqualTo(count);
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
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (token != null) request.header(header, token);
            request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) request.header("Content-Type", "application/json");
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { http.close(); }
    }
}
