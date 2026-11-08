package pe.edu.unp.lanyan.academic.api;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.shared.api.PageView;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.*;
import pe.edu.unp.lanyan.academic.application.*;

@RestController
@RequestMapping("/api/v1/academic")
public class AcademicAdminController {
    private final CourseService courses;
    private final CourseSectionService sections;
    private final EnrollmentService enrollments;

    public AcademicAdminController(CourseService courses, CourseSectionService sections, EnrollmentService enrollments) {
        this.courses = courses; this.sections = sections; this.enrollments = enrollments;
    }

    @PostMapping("/courses")
    public ResponseEntity<CourseView> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        var course = courses.create(request);
        return ResponseEntity.created(URI.create("/api/v1/academic/courses/" + course.id())).body(course);
    }

    @GetMapping("/courses")
    public PageView<CourseView> courses(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return courses.list(page, size);
    }

    @GetMapping("/courses/{id}")
    public CourseView course(@PathVariable UUID id) { return courses.get(id); }

    @PostMapping("/courses/{courseId}/sections")
    public ResponseEntity<SectionView> createSection(@PathVariable UUID courseId, @Valid @RequestBody CreateSectionRequest request) {
        var section = sections.create(courseId, request);
        return ResponseEntity.created(URI.create("/api/v1/academic/sections/" + section.id())).body(section);
    }

    @GetMapping("/courses/{courseId}/sections")
    public PageView<SectionView> sections(@PathVariable UUID courseId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return sections.list(courseId, page, size); }

    @GetMapping("/sections/{id}")
    public SectionView section(@PathVariable UUID id) { return sections.get(id); }

    @PostMapping("/enrollments")
    public ResponseEntity<EnrollmentView> createEnrollment(@Valid @RequestBody CreateEnrollmentRequest request) {
        var enrollment = enrollments.create(request);
        return ResponseEntity.created(URI.create("/api/v1/academic/enrollments/" + enrollment.id())).body(enrollment);
    }

    @GetMapping("/enrollments")
    public PageView<EnrollmentView> enrollments(@RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID sectionId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return enrollments.list(studentProfileId, sectionId, page, size);
    }

    @GetMapping("/enrollments/{id}")
    public EnrollmentView enrollment(@PathVariable UUID id) { return enrollments.get(id); }
}
