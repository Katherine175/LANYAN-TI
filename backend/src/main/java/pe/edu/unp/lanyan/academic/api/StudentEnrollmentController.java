package pe.edu.unp.lanyan.academic.api;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.academic.api.AcademicDtos.EnrollmentView;
import pe.edu.unp.lanyan.academic.application.EnrollmentService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/students/me/enrollments")
public class StudentEnrollmentController {
    public record SelfEnrollmentRequest(@NotNull UUID sectionId) { }

    private final EnrollmentService enrollments;
    public StudentEnrollmentController(EnrollmentService enrollments) { this.enrollments = enrollments; }

    @PostMapping
    public ResponseEntity<EnrollmentView> enroll(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SelfEnrollmentRequest request) {
        var enrollment = enrollments.enrollSelf(principal.getId(), request.sectionId());
        return ResponseEntity.created(URI.create("/api/v1/students/me/courses")).body(enrollment);
    }
}
