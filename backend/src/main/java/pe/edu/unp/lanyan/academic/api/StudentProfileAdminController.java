package pe.edu.unp.lanyan.academic.api;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import pe.edu.unp.lanyan.academic.application.StudentProfileService;

@RestController
@RequestMapping("/api/v1/academic/students/{userId}/profile")
public class StudentProfileAdminController {
    public record SaveStudentProfileRequest(@NotBlank @Size(max = 32) String studentCode, LocalDate admissionDate) { }

    private final StudentProfileService profiles;
    public StudentProfileAdminController(StudentProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public StudentProfileView get(@PathVariable UUID userId) { return profiles.getForUser(userId); }

    @PutMapping
    public StudentProfileView save(@PathVariable UUID userId, @Valid @RequestBody SaveStudentProfileRequest request) {
        return profiles.saveForUser(userId, request.studentCode(), request.admissionDate());
    }
}
