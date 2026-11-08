package pe.edu.unp.lanyan.academic.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.unp.lanyan.academic.application.StudentProfileService;
import pe.edu.unp.lanyan.identity.infrastructure.AccountPrincipal;

@RestController
@RequestMapping("/api/v1/students")
public class StudentProfileController {
    private final StudentProfileService profiles;

    public StudentProfileController(StudentProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/me")
    public StudentProfileView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.getMyProfile(principal.getId());
    }
}
