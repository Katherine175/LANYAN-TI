package pe.edu.unp.lanyan;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
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
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.unp.lanyan.academic.application.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import pe.edu.unp.lanyan.identity.domain.*;
import pe.edu.unp.lanyan.identity.infrastructure.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="lanyan.auth.login-limit=500")
class AcademicContentSecurityTest {
    // Fictitious fixtures are confined to this temporary PostgreSQL container.
    static final String PASSWORD="Fictitious-Content-Test42!";
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired Environment environment;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired StudentProfileRepository profiles;
    @Autowired CourseRepository courses;
    @Autowired CourseSectionRepository sections;
    @Autowired EnrollmentRepository enrollments;
    @Autowired ClassScheduleRepository schedules;
    @Autowired CourseMaterialRepository materials;
    @Autowired ClassScheduleService scheduleService;
    @Autowired CourseMaterialService materialService;
    @Autowired AccountDetailsService accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        if (users.count()!=0) return;
        String hash=passwords.encode(PASSWORD);
        account("admin@example.test",RoleName.ROLE_ADMIN,hash);
        account("user@example.test",RoleName.ROLE_USER,hash);
        account("technician@example.test",RoleName.ROLE_TECHNICIAN,hash);
        account("missing@example.test",RoleName.ROLE_STUDENT,hash);
    }
    UserAccount account(String email,RoleName role,String hash) {
        return users.saveAndFlush(new UserAccount("Persona ficticia",email,hash,roles.findAllByNameIn(Set.of(role))));
    }
    StudentProfile student() {
        String suffix=UUID.randomUUID().toString().substring(0,12);
        var user=account("s-"+suffix+"@example.test",RoleName.ROLE_STUDENT,users.findByEmail("admin@example.test").orElseThrow().getPasswordHash());
        return profiles.saveAndFlush(new StudentProfile(user,"TEST-"+suffix,null));
    }
    CourseSection section() {
        var c=courses.saveAndFlush(new Course("TEST-"+UUID.randomUUID().toString().substring(0,12),"Curso ficticio","Idioma ficticio",null,null,null));
        return sections.saveAndFlush(new CourseSection(c,"A","TEST-2026",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),20,null));
    }
    void enroll(StudentProfile p,CourseSection s) { enrollments.saveAndFlush(new Enrollment(p,s)); }
    String collection(CourseSection s,String resource) { return "/api/v1/academic/sections/"+s.getId()+"/"+resource; }
    String item(String resource,String id) { return "/api/v1/academic/"+resource+"/"+id; }
    String id(HttpResponse<String> r) { return JsonPath.read(r.body(),"$.id"); }
    String schedule(String day,String start,String end,Long version) {
        return "{\"dayOfWeek\":\""+day+"\",\"startTime\":\""+start+"\",\"endTime\":\""+end+"\",\"modality\":\"IN_PERSON\",\"classroom\":\"Aula ficticia\""
                +(version==null?"":",\"version\":"+version)+"}";
    }
    String schedule() { return schedule("MONDAY","08:00","09:00",null); }
    String material(String reference,String status,Long version) {
        return "{\"title\":\"Guia ficticia\",\"type\":\"GUIDE\",\"referenceType\":\"HTTPS_URL\",\"reference\":\""+reference+"\",\"status\":\""+status+"\""
                +(version==null?"":",\"version\":"+version)+"}";
    }
    String material() { return material("https://example.org/guide.pdf","PUBLISHED",null); }
    HttpResponse<String> create(Client c,CourseSection s,String resource,String body) throws Exception {
        var r=c.send("POST",collection(s,resource),body);
        assertThat(r.statusCode()).withFailMessage("%s",r.body()).isEqualTo(201);
        return r;
    }
    int count(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage("%s",response.body()).isEqualTo(200);
        return JsonPath.read(response.body(),"$.totalElements");
    }

    @ParameterizedTest
    @ValueSource(strings={"schedules","materials"})
    void adminCrudPreservesRetiredRowsAndRejectsStaleVersions(String resource) throws Exception {
        var section=section();
        String body=resource.equals("schedules")?schedule():material();
        try (var admin=new Client()) {
            admin.login("admin@example.test");
            var created=create(admin,section,resource,body);
            String path=item(resource,id(created));
            assertThat(created.headers().firstValue("location")).contains(path);
            assertThat(JsonPath.<Integer>read(created.body(),"$.version")).isZero();
            assertThat(admin.get(path).statusCode()).isEqualTo(200);
            assertThat(count(admin.get(collection(section,resource)))).isEqualTo(1);
            assertThat(admin.send("PUT",path,body).statusCode()).isEqualTo(400);
            var updated=admin.send("PUT",path,resource.equals("schedules")?schedule("TUESDAY","10:00","11:00",0L):material("https://example.org/revised.pdf","DRAFT",0L));
            assertThat(updated.statusCode()).withFailMessage("%s",updated.body()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(updated.body(),"$.version")).isEqualTo(1);
            String staleBody=resource.equals("schedules")?schedule("FRIDAY","10:00","11:00",0L):material("https://example.org/stale.pdf","PUBLISHED",0L);
            assertThat(admin.send("PUT",path,staleBody).statusCode()).isEqualTo(409);
            assertThat(admin.send("DELETE",path,null).statusCode()).isEqualTo(400);
            assertThat(admin.send("DELETE",path+"?version=0",null).statusCode()).isEqualTo(409);
            assertThat(admin.send("DELETE",path+"?version=1",null).statusCode()).isEqualTo(204);
            assertThat(admin.get(path).statusCode()).isEqualTo(404);
            assertThat(admin.send("DELETE",path+"?version=2",null).statusCode()).isEqualTo(404);
            assertThat(admin.send("PUT",path,staleBody).statusCode()).isEqualTo(404);
            assertThat(count(admin.get(collection(section,resource)))).isZero();
            assertThat(jdbc.queryForObject("select count(*) from academic_"+(resource.equals("schedules")?"class_schedules":"course_materials")+" where id=? and deleted_at is not null",Integer.class,UUID.fromString(id(created)))).isEqualTo(1);
            create(admin,section,resource,resource.equals("schedules")?schedule("TUESDAY","10:00","11:00",null):material("https://example.org/revised.pdf","PUBLISHED",null));
        }
    }

    @Test
    void ownEndpointsExcludeOtherStudentsDraftsAndRemovedContent() throws Exception {
        var a=student(); var b=student(); var sa=section(); var sb=section();
        enroll(a,sa); enroll(b,sb);
        try(var admin=new Client();var ca=new Client();var cb=new Client()) {
            admin.login("admin@example.test");
            var scheduleA=create(admin,sa,"schedules",schedule());
            var scheduleB=create(admin,sb,"schedules",schedule());
            var materialA=create(admin,sa,"materials",material());
            var materialB=create(admin,sb,"materials",material());
            create(admin,sa,"materials",material("https://example.org/private-draft.pdf","DRAFT",null));
            ca.login(a.getUser().getEmail()); cb.login(b.getUser().getEmail());
            for(String resource:List.of("schedule","materials")) {
                var response=ca.get("/api/v1/students/me/"+resource+"?userId="+b.getUser().getId()+"&studentProfileId="+b.getId()+"&sectionId="+sb.getId());
                assertThat(count(response)).isEqualTo(1);
                assertThat(response.body()).contains(sa.getId().toString()).doesNotContain(sb.getId().toString(),b.getUser().getEmail(),"private-draft","password","studentProfileId");
                assertThat(count(cb.get("/api/v1/students/me/"+resource))).isEqualTo(1);
                assertThat(ca.get("/api/v1/students/"+b.getUser().getId()+"/"+resource).statusCode()).isEqualTo(403);
            }
            assertThat(ca.get(item("materials",id(materialB))).statusCode()).isEqualTo(403);
            assertThat(ca.get(item("schedules",id(scheduleB))).statusCode()).isEqualTo(403);
            assertThat(admin.send("DELETE",item("schedules",id(scheduleA))+"?version=0",null).statusCode()).isEqualTo(204);
            assertThat(admin.send("DELETE",item("materials",id(materialA))+"?version=0",null).statusCode()).isEqualTo(204);
            assertThat(count(ca.get("/api/v1/students/me/schedule"))).isZero();
            assertThat(count(ca.get("/api/v1/students/me/materials"))).isZero();
        }
    }

    @Test
    void publishingAndUnpublishingChangesVisibilityImmediately() throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            var draft=create(admin,s,"materials",material("https://example.org/draft","DRAFT",null));
            String path=item("materials",id(draft));
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isZero();
            assertThat(admin.send("PUT",path,material("https://example.org/draft","PUBLISHED",0L)).statusCode()).isEqualTo(200);
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isEqualTo(1);
            assertThat(admin.send("PUT",path,material("https://example.org/draft","DRAFT",1L)).statusCode()).isEqualTo(200);
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"CANCELLED","COMPLETED"})
    void nonCurrentEnrollmentsCannotReadContent(String status) throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            create(admin,s,"materials",material());create(admin,s,"schedules",schedule());
            jdbc.update("update academic_enrollments set status=? where student_profile_id=?",status,p.getId());
            assertThat(count(reader.get("/api/v1/students/me/schedule"))).isZero();
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isZero();
        }
    }

    @Test
    void closedSectionsRemainReadableButCancelledSectionsDoNot() throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            create(admin,s,"materials",material());create(admin,s,"schedules",schedule());
            jdbc.update("update academic_course_sections set status='CLOSED' where id=?",s.getId());
            assertThat(count(reader.get("/api/v1/students/me/schedule"))).isEqualTo(1);
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isEqualTo(1);
            jdbc.update("update academic_course_sections set status='CANCELLED' where id=?",s.getId());
            assertThat(count(reader.get("/api/v1/students/me/schedule"))).isZero();
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isZero();
        }
    }

    @Test
    void emptyProfilesAndMissingProfilesHaveExplicitResponses() throws Exception {
        var p=student();
        try(var empty=new Client();var missing=new Client()) {
            empty.login(p.getUser().getEmail());missing.login("missing@example.test");
            for(String resource:List.of("schedule","materials")) {
                assertThat(count(empty.get("/api/v1/students/me/"+resource))).isZero();
                var result=missing.get("/api/v1/students/me/"+resource);
                assertThat(result.statusCode()).isEqualTo(404);
                assertThat(result.body()).contains("STUDENT_PROFILE_NOT_FOUND");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"user@example.test","technician@example.test","missing@example.test"})
    void nonAdminsCannotUseAnyContentCrud(String email) throws Exception {
        try(var c=new Client()) {
            c.login(email);
            for(String resource:List.of("schedules","materials")) {
                String coll="/api/v1/academic/sections/"+UUID.randomUUID()+"/"+resource;
                String path=item(resource,UUID.randomUUID().toString());
                assertThat(c.get(coll).statusCode()).isEqualTo(403);
                assertThat(c.get(path).statusCode()).isEqualTo(403);
                assertThat(c.send("POST",coll,resource.equals("schedules")?schedule():material()).statusCode()).isEqualTo(403);
                assertThat(c.send("PUT",path,resource.equals("schedules")?schedule():material()).statusCode()).isEqualTo(403);
                assertThat(c.send("DELETE",path+"?version=0",null).statusCode()).isEqualTo(403);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"admin@example.test","user@example.test","technician@example.test"})
    void nonStudentsCannotReadStudentContent(String email) throws Exception {
        try(var c=new Client()) {
            c.login(email);
            assertThat(c.get("/api/v1/students/me/schedule").statusCode()).isEqualTo(403);
            assertThat(c.get("/api/v1/students/me/materials").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void anonymousAccessAndEveryUnsafeVerbRequireAuthenticationAndCsrf() throws Exception {
        var s=section();
        try(var anon=new Client();var admin=new Client()) {
            for(String path:List.of("/api/v1/students/me/schedule","/api/v1/students/me/materials",collection(s,"schedules"),collection(s,"materials")))
                assertThat(anon.get(path).statusCode()).isEqualTo(401);
            anon.csrf();
            assertThat(anon.send("POST",collection(s,"materials"),material()).statusCode()).isEqualTo(401);
            admin.login("admin@example.test");
            for(String resource:List.of("schedules","materials")) {
                String body=resource.equals("schedules")?schedule():material();
                var r=create(admin,s,resource,body);
                admin.token=null;
                assertThat(admin.send("POST",collection(s,resource),body).statusCode()).isEqualTo(403);
                assertThat(admin.send("PUT",item(resource,id(r)),body).statusCode()).isEqualTo(403);
                assertThat(admin.send("DELETE",item(resource,id(r))+"?version=0",null).statusCode()).isEqualTo(403);
                admin.token="invalid-token";
                assertThat(admin.send("DELETE",item(resource,id(r))+"?version=0",null).statusCode()).isEqualTo(403);
                admin.csrf();
            }
        }
    }

    @Test
    void methodSecurityChecksPermissionsAndOwnershipInAdditionToRoles() {
        var p=student();var principal=accounts.refresh(p.getUser().getId());
        var context=SecurityContextHolder.createEmptyContext();SecurityContextHolder.setContext(context);
        try {
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
            assertThatThrownBy(()->scheduleService.mySchedule(UUID.randomUUID(),0,20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->materialService.myMaterials(UUID.randomUUID(),0,20)).isInstanceOf(AccessDeniedException.class);
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_STUDENT"),new SimpleGrantedAuthority("portal:academic:access"))));
            assertThatThrownBy(()->scheduleService.mySchedule(p.getUser().getId(),0,20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->materialService.myMaterials(p.getUser().getId(),0,20)).isInstanceOf(AccessDeniedException.class);
            var admin=accounts.refresh(users.findByEmail("admin@example.test").orElseThrow().getId());
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(admin,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThatThrownBy(()->scheduleService.list(UUID.randomUUID(),0,20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->materialService.list(UUID.randomUUID(),0,20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->scheduleService.create(UUID.randomUUID(),null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->materialService.create(UUID.randomUUID(),null)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void revocationAndDeactivationAreEffectiveWithoutDeletingAcademicData() throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            create(admin,s,"materials",material());create(admin,s,"schedules",schedule());
            assertThat(admin.send("PUT","/api/v1/users/"+p.getUser().getId()+"/roles","{\"roles\":[\"ROLE_USER\"]}").statusCode()).isEqualTo(200);
            assertThat(reader.get("/api/v1/students/me/schedule").statusCode()).isEqualTo(403);
            assertThat(reader.get("/api/v1/students/me/materials").statusCode()).isEqualTo(403);
            jdbc.update("update identity_users set active=false where id=?",p.getUser().getId());
            assertThat(reader.get("/api/v1/students/me/materials").statusCode()).isEqualTo(401);
            assertThat(enrollments.existsByStudent_IdAndSection_Id(p.getId(),s.getId())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from academic_course_materials where section_id=?",Integer.class,s.getId())).isEqualTo(1);
        }
    }

    @Test
    void overlapIncludesContainmentButAdjacentSlotsAndOtherDaysAreAllowed() throws Exception {
        var s=section();
        try(var admin=new Client()) {
            admin.login("admin@example.test");
            var first=create(admin,s,"schedules",schedule());
            for(String[] time:List.of(new String[]{"08:00","09:00"},new String[]{"08:30","09:30"},new String[]{"07:30","08:30"},new String[]{"07:00","10:00"},new String[]{"08:15","08:45"}))
                assertThat(admin.send("POST",collection(s,"schedules"),schedule("MONDAY",time[0],time[1],null)).statusCode()).isEqualTo(409);
            var adjacent=create(admin,s,"schedules",schedule("MONDAY","09:00","10:00",null));
            create(admin,s,"schedules",schedule("TUESDAY","08:00","09:00",null));
            assertThat(admin.send("PUT",item("schedules",id(adjacent)),schedule("MONDAY","08:30","10:00",0L)).statusCode()).isEqualTo(409);
            assertThat(JsonPath.<Integer>read(admin.get(item("schedules",id(first))).body(),"$.version")).isZero();
            assertThat(count(admin.get(collection(s,"schedules")))).isEqualTo(3);
        }
    }

    @Test
    void normalizedReferencesAreUniquePerSectionAndUpdateConflictsRollback() throws Exception {
        var s=section();var other=section();
        try(var admin=new Client()) {
            admin.login("admin@example.test");
            create(admin,s,"materials",material());
            assertThat(admin.send("POST",collection(s,"materials"),material("HTTPS://EXAMPLE.ORG:443/guide.pdf","DRAFT",null)).statusCode()).isEqualTo(409);
            var second=create(admin,s,"materials",material("https://example.org/other.pdf","PUBLISHED",null));
            assertThat(admin.send("PUT",item("materials",id(second)),material("https://example.org/guide.pdf","PUBLISHED",0L)).statusCode()).isEqualTo(409);
            assertThat(admin.get(item("materials",id(second))).body()).contains("other.pdf");
            create(admin,other,"materials",material());
        }
    }

    @Test
    void invalidBodiesReferencesAndMissingRelationsAreRejected() throws Exception {
        var s=section();
        try(var admin=new Client()) {
            admin.login("admin@example.test");
            for(String resource:List.of("schedules","materials")) {
                assertThat(admin.send("POST",collection(s,resource),"{}").statusCode()).isEqualTo(400);
                assertThat(admin.send("POST",collection(s,resource),"not-json").statusCode()).isEqualTo(400);
                assertThat(admin.get(item(resource,UUID.randomUUID().toString())).statusCode()).isEqualTo(404);
                assertThat(admin.get("/api/v1/academic/sections/"+UUID.randomUUID()+"/"+resource).statusCode()).isEqualTo(404);
                assertThat(admin.send("POST","/api/v1/academic/sections/"+UUID.randomUUID()+"/"+resource,resource.equals("schedules")?schedule():material()).statusCode()).isEqualTo(404);
                assertThat(admin.get(item(resource,"not-a-uuid")).statusCode()).isEqualTo(400);
            }
            for(String body:List.of(schedule("INVALID","08:00","09:00",null),schedule("MONDAY","09:00","08:00",null),
                    schedule("MONDAY","08:00:01","09:00",null),schedule().replace("Aula ficticia"," "),schedule().replace("IN_PERSON","UNKNOWN")))
                assertThat(admin.send("POST",collection(s,"schedules"),body).statusCode()).isEqualTo(400);
            for(String ref:List.of("http://example.org/a","https://127.0.0.1/a","javascript:alert(1)","https://user@example.org/a"))
                assertThat(admin.send("POST",collection(s,"materials"),material(ref,"PUBLISHED",null)).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST",collection(s,"materials"),material().replace("PUBLISHED","UNKNOWN")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST",collection(s,"materials"),material().replace("Guia ficticia"," ")).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void paginationIsBoundedAndScheduleOrderingIsWeekly() throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            create(admin,s,"schedules",schedule("FRIDAY","10:00","11:00",null));
            create(admin,s,"schedules",schedule("MONDAY","09:00","10:00",null));
            create(admin,s,"schedules",schedule());
            create(admin,s,"materials",material());
            create(admin,s,"materials",material("https://example.org/second","PUBLISHED",null));
            var first=reader.get("/api/v1/students/me/schedule?size=1");
            assertThat(count(first)).isEqualTo(3);
            assertThat(JsonPath.<String>read(first.body(),"$.content[0].dayOfWeek")).isEqualTo("MONDAY");
            assertThat(JsonPath.<String>read(first.body(),"$.content[0].startTime")).startsWith("08:00");
            assertThat(JsonPath.<String>read(first.body(),"$.content[0].timeZone")).isEqualTo("America/Lima");
            assertThat(count(reader.get("/api/v1/students/me/materials?size=1"))).isEqualTo(2);
            for(String path:List.of("/api/v1/students/me/schedule","/api/v1/students/me/materials")) {
                assertThat(reader.get(path+"?page=-1").statusCode()).isEqualTo(400);
                assertThat(reader.get(path+"?size=101").statusCode()).isEqualTo(400);
            }
            for(String resource:List.of("schedules","materials")) assertThat(admin.get(collection(s,resource)+"?size=0").statusCode()).isEqualTo(400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"schedules","materials"})
    void concurrentDuplicatePostsProduceOnlyOneActiveRecord(String resource) throws Exception {
        var s=section();String body=resource.equals("schedules")?schedule():material();
        try(var a=new Client();var b=new Client();var executor=Executors.newFixedThreadPool(2)) {
            a.login("admin@example.test");b.login("admin@example.test");
            var gate=new CountDownLatch(1);
            var first=executor.submit(()->{gate.await();return a.send("POST",collection(s,resource),body).statusCode();});
            var second=executor.submit(()->{gate.await();return b.send("POST",collection(s,resource),body).statusCode();});
            gate.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(count(a.get(collection(s,resource)))).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"schedules","materials"})
    void concurrentEditorsCannotOverwriteTheSameVersion(String resource) throws Exception {
        var s=section();
        try(var a=new Client();var b=new Client();var executor=Executors.newFixedThreadPool(2)) {
            a.login("admin@example.test");b.login("admin@example.test");
            var created=create(a,s,resource,resource.equals("schedules")?schedule():material());
            String path=item(resource,id(created));
            String bodyA=resource.equals("schedules")?schedule("TUESDAY","09:00","10:00",0L):material("https://example.org/editor-a","PUBLISHED",0L);
            String bodyB=resource.equals("schedules")?schedule("FRIDAY","12:00","13:00",0L):material("https://example.org/editor-b","DRAFT",0L);
            var gate=new CountDownLatch(1);
            var first=executor.submit(()->{gate.await();return a.send("PUT",path,bodyA).statusCode();});
            var second=executor.submit(()->{gate.await();return b.send("PUT",path,bodyB).statusCode();});
            gate.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(JsonPath.<Integer>read(a.get(path).body(),"$.version")).isEqualTo(1);
            assertThat(count(a.get(collection(s,resource)))).isEqualTo(1);
        }
    }

    @Test
    void localReferencesAreMetadataOnlyAndHaveCaseInsensitiveUniqueness() throws Exception {
        var p=student();var s=section();enroll(p,s);
        try(var admin=new Client();var reader=new Client()) {
            admin.login("admin@example.test");reader.login(p.getUser().getEmail());
            String body="{\"title\":\"Documento ficticio\",\"type\":\"DOCUMENT\",\"referenceType\":\"LOCAL_REFERENCE\",\"reference\":\"Cursos/Guia.pdf\",\"status\":\"PUBLISHED\"}";
            var created=create(admin,s,"materials",body);
            assertThat(JsonPath.<String>read(created.body(),"$.reference")).isEqualTo("cursos/guia.pdf");
            assertThat(admin.send("POST",collection(s,"materials"),body.replace("Cursos/Guia.pdf","cursos/guia.pdf")).statusCode()).isEqualTo(409);
            assertThat(admin.send("POST",collection(s,"materials"),body.replace("Cursos/Guia.pdf","../secret.pdf")).statusCode()).isEqualTo(400);
            assertThat(admin.send("POST",collection(s,"materials"),body.replace("DOCUMENT","LINK")).statusCode()).isEqualTo(400);
            assertThat(count(reader.get("/api/v1/students/me/materials"))).isEqualTo(1);
            // No download/file-serving route is introduced for the metadata reference.
            assertThat(reader.get("/api/v1/students/me/materials/"+id(created)+"/download").statusCode()).isEqualTo(403);
        }
    }

    @Test
    void databaseConstraintsProtectForeignKeysEnumsTimesAndUniqueness() throws Exception {
        var s=section();
        try(var admin=new Client()) {
            admin.login("admin@example.test");
            var sr=create(admin,s,"schedules",schedule());
            var mr=create(admin,s,"materials",material());
            UUID scheduleId=UUID.fromString(id(sr)),materialId=UUID.fromString(id(mr));
            for(String sql:List.of("update academic_class_schedules set day_of_week=8 where id=?",
                    "update academic_class_schedules set end_time=start_time where id=?",
                    "update academic_class_schedules set start_time='08:00:01' where id=?",
                    "update academic_class_schedules set classroom=null where id=?",
                    "update academic_class_schedules set modality='UNKNOWN' where id=?"))
                assertThatThrownBy(()->jdbc.update(sql,scheduleId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("update academic_class_schedules set section_id=? where id=?",UUID.randomUUID(),scheduleId)).isInstanceOf(DataIntegrityViolationException.class);
            for(String sql:List.of("update academic_course_materials set status='UNKNOWN' where id=?",
                    "update academic_course_materials set type='UNKNOWN' where id=?",
                    "update academic_course_materials set title=' ' where id=?",
                    "update academic_course_materials set reference='javascript:alert(1)' where id=?"))
                assertThatThrownBy(()->jdbc.update(sql,materialId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("update academic_course_materials set section_id=? where id=?",UUID.randomUUID(),materialId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("insert into academic_class_schedules(id,section_id,day_of_week,start_time,end_time,modality,classroom) select ?,section_id,day_of_week,start_time,end_time,modality,classroom from academic_class_schedules where id=?",UUID.randomUUID(),scheduleId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("insert into academic_course_materials(id,section_id,title,type,reference_type,reference,status) select ?,section_id,title,type,reference_type,reference,status from academic_course_materials where id=?",UUID.randomUUID(),materialId)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private final class Client implements AutoCloseable {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
        String token;String header="X-CSRF-TOKEN";
        HttpResponse<String> get(String path) throws Exception { return send("GET",path,null); }
        void csrf() throws Exception {
            var response=get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            token=JsonPath.read(response.body(),"$.token");header=JsonPath.read(response.body(),"$.headerName");
        }
        void login(String email) throws Exception {
            csrf();
            var r=send("POST","/api/v1/auth/login","{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}");
            assertThat(r.statusCode()).withFailMessage("%s",r.body()).isEqualTo(200);csrf();
        }
        HttpResponse<String> send(String method,String path,String json) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getRequiredProperty("local.server.port")+path))
                    .timeout(Duration.ofSeconds(20)).header("Accept","application/json");
            if(token!=null) builder.header(header,token);
            if(json!=null) builder.header("Content-Type","application/json");
            builder.method(method,json==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json));
            return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        public void close() { http.close(); }
    }
}
