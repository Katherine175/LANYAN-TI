package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.StudentProfileView;
import pe.edu.unp.lanyan.academic.infrastructure.StudentProfileRepository;

@Service
@Transactional(readOnly = true)
public class StudentProfileService {
    private final StudentProfileRepository profiles;

    public StudentProfileService(StudentProfileRepository profiles) { this.profiles = profiles; }

    @PreAuthorize("hasRole('STUDENT') and hasAuthority('portal:academic:access') and #userId == authentication.principal.id")
    public StudentProfileView getMyProfile(UUID userId) {
        var profile = profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new);
        var user = profile.getUser();
        return new StudentProfileView(profile.getId(), user.getId(), profile.getStudentCode(),
                user.getFullName(), user.getEmail(), profile.getAdmissionDate(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
