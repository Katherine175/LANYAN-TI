package pe.edu.unp.lanyan.academic.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.application.EnrollmentService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/students/me/courses")
public class StudentCoursesController {
    private final EnrollmentService enrollments;
    public StudentCoursesController(EnrollmentService enrollments) { this.enrollments = enrollments; }

    @GetMapping
    public PageView<StudentCourseView> courses(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return enrollments.myCourses(principal.getId(), page, size);
    }
}
