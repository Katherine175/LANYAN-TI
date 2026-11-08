package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.domain.Course;
import pe.edu.unp.lanyan.academic.infrastructure.CourseRepository;

@Service
@Transactional(readOnly = true)
public class CourseService {
    private final CourseRepository courses;
    public CourseService(CourseRepository courses) { this.courses = courses; }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:courses:manage')")
    public CourseView create(CreateCourseRequest request) {
        var course = new Course(request.code(), request.name(), request.language(), request.level(), request.description(), request.status());
        if (courses.existsByCode(course.getCode())) {
            throw new AcademicException(HttpStatus.CONFLICT, "COURSE_CODE_EXISTS", "El código del curso ya está registrado.");
        }
        return AcademicViews.course(courses.saveAndFlush(course));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public PageView<CourseView> list(int page, int size) {
        return PageView.of(courses.findAll(AcademicPages.request(page, size, "createdAt")).map(AcademicViews::course));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:read')")
    public CourseView get(UUID id) {
        return AcademicViews.course(courses.findById(id).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Curso no encontrado.")));
    }
}
