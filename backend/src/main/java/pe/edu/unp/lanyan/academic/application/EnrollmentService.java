package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.domain.*;
import pe.edu.unp.lanyan.academic.infrastructure.*;
import pe.edu.unp.lanyan.identity.domain.RoleName;

@Service
@Transactional(readOnly = true)
public class EnrollmentService {
    private final StudentProfileRepository profiles;
    private final CourseSectionRepository sections;
    private final EnrollmentRepository enrollments;

    public EnrollmentService(StudentProfileRepository profiles, CourseSectionRepository sections, EnrollmentRepository enrollments) {
        this.profiles = profiles; this.sections = sections; this.enrollments = enrollments;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:enrollments:manage')")
    public EnrollmentView create(CreateEnrollmentRequest request) {
        // Orden de bloqueo constante: primero el perfil, después la sección.
        var student = profiles.findForEnrollment(request.studentProfileId()).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND", "Perfil estudiantil no encontrado."));
        if (!student.getUser().isActive() || student.getUser().getRoles().stream().noneMatch(role -> role.getName() == RoleName.ROLE_STUDENT)) {
            throw new AcademicException(HttpStatus.CONFLICT, "STUDENT_NOT_ELIGIBLE", "La cuenta del estudiante debe estar activa y tener rol STUDENT.");
        }
        var section = sections.findForEnrollment(request.sectionId()).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Sección no encontrada."));
        if (section.getCourse().getStatus() != CourseStatus.ACTIVE) {
            throw new AcademicException(HttpStatus.CONFLICT, "COURSE_INACTIVE", "El curso no admite nuevas matrículas.");
        }
        if (section.getStatus() != SectionStatus.OPEN) {
            throw new AcademicException(HttpStatus.CONFLICT, "SECTION_NOT_OPEN", "La sección no está abierta a matrículas.");
        }
        if (enrollments.existsByStudent_IdAndSection_Id(student.getId(), section.getId())) {
            throw new AcademicException(HttpStatus.CONFLICT, "ENROLLMENT_EXISTS", "El estudiante ya tiene una matrícula en esa sección.");
        }
        if (enrollments.countBySection_IdAndStatus(section.getId(), EnrollmentStatus.ENROLLED) >= section.getCapacity()) {
            throw new AcademicException(HttpStatus.CONFLICT, "SECTION_FULL", "La sección no tiene cupos disponibles.");
        }
        return AcademicViews.enrollment(enrollments.saveAndFlush(new Enrollment(student, section)));
    }

    @PreAuthorize("hasRole('STUDENT') and hasAuthority('portal:academic:access') and hasAuthority('academic:enrollments:read:self') and #userId == authentication.principal.id")
    public PageView<StudentCourseView> myCourses(UUID userId, int page, int size) {
        var pagination = AcademicPages.request(page, size, "enrolledAt");
        var profile = profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new);
        return PageView.of(enrollments.findByStudent_Id(profile.getId(), pagination).map(AcademicViews::studentCourse));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public PageView<EnrollmentView> list(UUID studentProfileId, UUID sectionId, int page, int size) {
        var pagination = AcademicPages.request(page, size, "enrolledAt");
        Page<Enrollment> result;
        if (studentProfileId != null && sectionId != null) result = enrollments.findByStudent_IdAndSection_Id(studentProfileId, sectionId, pagination);
        else if (studentProfileId != null) result = enrollments.findByStudent_Id(studentProfileId, pagination);
        else if (sectionId != null) result = enrollments.findBySection_Id(sectionId, pagination);
        else result = enrollments.findAll(pagination);
        return PageView.of(result.map(AcademicViews::enrollment));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public EnrollmentView get(UUID id) {
        return AcademicViews.enrollment(enrollments.findById(id).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Matrícula no encontrada.")));
    }
}
