package pe.edu.unp.lanyan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.application.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import pe.edu.unp.lanyan.identity.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class AcademicEnrollmentRulesTest {
    StudentProfileRepository profiles;
    CourseSectionRepository sections;
    EnrollmentRepository enrollments;
    EnrollmentService service;
    StudentProfile student;
    Course course;
    CourseSection section;

    @BeforeEach
    void fixture() {
        profiles = mock(StudentProfileRepository.class);
        sections = mock(CourseSectionRepository.class);
        enrollments = mock(EnrollmentRepository.class);
        service = new EnrollmentService(profiles, sections, enrollments, org.mockito.Mockito.mock(pe.edu.unp.lanyan.audit.application.AuditService.class));
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.ROLE_STUDENT);
        var account = new UserAccount("Estudiante FICTICIO", "student@example.test", "NOT_A_REAL_HASH", Set.of(role));
        student = new StudentProfile(account, "TEST-STUDENT", null);
        course = new Course(" test-course ", "Curso FICTICIO", "Idioma FICTICIO", null, null, null);
        section = section(course, SectionStatus.OPEN, 1);
        when(profiles.findForEnrollment(student.getId())).thenReturn(Optional.of(student));
        when(sections.findForEnrollment(section.getId())).thenReturn(Optional.of(section));
        when(enrollments.saveAndFlush(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    CourseSection section(Course course, SectionStatus status, int capacity) {
        return new CourseSection(course, " a ", " test-2026 ", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), capacity, status);
    }

    CreateEnrollmentRequest request() { return new CreateEnrollmentRequest(student.getId(), section.getId()); }

    @Test
    void courseAndSectionNormalizeKeysAndSetSafeDefaults() {
        assertThat(course.getCode()).isEqualTo("TEST-COURSE");
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
        assertThat(course.getLevel()).isNull();
        assertThat(section.getCode()).isEqualTo("A");
        assertThat(section.getAcademicPeriod()).isEqualTo("TEST-2026");
        assertThat(section.getCourse()).isSameAs(course);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankCourseCodesAreRejected(String code) {
        assertThatThrownBy(() -> new Course(code, "Curso FICTICIO", "Idioma FICTICIO", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidDateRangesAndCapacitiesAreRejected() {
        assertThatThrownBy(() -> section(course, SectionStatus.OPEN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseSection(course, "A", "TEST", LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1), 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enrollmentUsesTheCourseFromItsSection() {
        var result = service.create(request());
        assertThat(result.studentProfileId()).isEqualTo(student.getId());
        assertThat(result.sectionId()).isEqualTo(section.getId());
        assertThat(result.courseId()).isEqualTo(course.getId());
        assertThat(result.status()).isEqualTo(EnrollmentStatus.ENROLLED);
        verify(enrollments).countBySection_IdAndStatus(section.getId(), EnrollmentStatus.ENROLLED);
    }

    @Test
    void duplicatesAreRejectedBeforeWritingAnotherRow() {
        when(enrollments.existsByStudent_IdAndSection_Id(student.getId(), section.getId())).thenReturn(true);
        var ex = catchThrowableOfType(() -> service.create(request()), AcademicException.class);
        assertThat(ex.code()).isEqualTo("ENROLLMENT_EXISTS");
        verify(enrollments, never()).saveAndFlush(any());
    }

    @Test
    void aFullSectionDoesNotAcceptAnotherEnrollment() {
        when(enrollments.countBySection_IdAndStatus(section.getId(), EnrollmentStatus.ENROLLED)).thenReturn(1L);
        var ex = catchThrowableOfType(() -> service.create(request()), AcademicException.class);
        assertThat(ex.code()).isEqualTo("SECTION_FULL");
        verify(enrollments, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = SectionStatus.class, names = {"CLOSED", "CANCELLED"})
    void sectionsNotOpenRejectEnrollment(SectionStatus status) {
        section = section(course, status, 1);
        when(sections.findForEnrollment(section.getId())).thenReturn(Optional.of(section));
        assertThat(catchThrowableOfType(() -> service.create(request()), AcademicException.class).code()).isEqualTo("SECTION_NOT_OPEN");
    }

    @Test
    void inactiveCoursesRejectEnrollment() {
        course = new Course("INACTIVE", "Curso FICTICIO", "Idioma FICTICIO", null, null, CourseStatus.INACTIVE);
        section = section(course, SectionStatus.OPEN, 1);
        when(sections.findForEnrollment(section.getId())).thenReturn(Optional.of(section));
        assertThat(catchThrowableOfType(() -> service.create(request()), AcademicException.class).code()).isEqualTo("COURSE_INACTIVE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anInactiveAccountOrMissingStudentRoleIsNotEligible(boolean active) {
        var account = mock(UserAccount.class);
        when(account.isActive()).thenReturn(active);
        when(account.getRoles()).thenReturn(Set.of());
        var invalid = mock(StudentProfile.class);
        when(invalid.getUser()).thenReturn(account);
        when(profiles.findForEnrollment(student.getId())).thenReturn(Optional.of(invalid));
        assertThat(catchThrowableOfType(() -> service.create(request()), AcademicException.class).code()).isEqualTo("STUDENT_NOT_ELIGIBLE");
        verify(sections, never()).findForEnrollment(any());
    }

    @Test
    void unknownStudentProfileIsRejected() {
        when(profiles.findForEnrollment(student.getId())).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.create(request()), AcademicException.class).code()).isEqualTo("STUDENT_PROFILE_NOT_FOUND");
    }

    @Test
    void unknownSectionIsRejected() {
        when(sections.findForEnrollment(section.getId())).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.create(request()), AcademicException.class).code()).isEqualTo("SECTION_NOT_FOUND");
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPaginationIsRejectedBeforeQueryingPrivateData(int page, int size) {
        assertThat(catchThrowableOfType(() -> service.myCourses(UUID.randomUUID(), page, size), AcademicException.class).code()).isEqualTo("INVALID_PAGE");
        verify(profiles, never()).findByUser_Id(any());
    }

    @Test
    void studentReadUsesOnlyItsOwnProfileAndReturnsCourseAndSectionDtos() {
        var userId = student.getUser().getId();
        when(profiles.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(enrollments.findByStudent_Id(eq(student.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new Enrollment(student, section))));
        var result = service.myCourses(userId, 0, 20);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().course().id()).isEqualTo(result.content().getFirst().section().courseId());
        verify(enrollments).findByStudent_Id(eq(student.getId()), any(Pageable.class));
        verify(enrollments, never()).findAll(any(Pageable.class));
    }

    @Test
    void aMissingOwnProfileIsNotReplacedByAnotherStudentsProfile() {
        assertThatThrownBy(() -> service.myCourses(UUID.randomUUID(), 0, 20)).isInstanceOf(StudentProfileNotFoundException.class);
        verify(enrollments, never()).findAll(any(Pageable.class));
    }

    @Test
    void duplicateCourseCodesAreComparedAfterNormalization() {
        var repository = mock(CourseRepository.class);
        when(repository.existsByCode("TEST-CODE")).thenReturn(true);
        var request = new CreateCourseRequest(" test-code ", "Curso FICTICIO", "Idioma FICTICIO", null, null, null);
        assertThat(catchThrowableOfType(() -> new CourseService(repository).create(request), AcademicException.class).code()).isEqualTo("COURSE_CODE_EXISTS");
        verify(repository, never()).saveAndFlush(any());
    }
}
