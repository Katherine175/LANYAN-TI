package pe.edu.unp.lanyan.academic.application;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.academic.api.StudentProfileView;
import pe.edu.unp.lanyan.academic.domain.StudentProfile;
import pe.edu.unp.lanyan.academic.infrastructure.StudentProfileRepository;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly = true)
public class StudentProfileService {
    private final StudentProfileRepository profiles;
    private final UserAccountRepository users;
    private final AuditService audit;

    public StudentProfileService(StudentProfileRepository profiles, UserAccountRepository users, AuditService audit) {
        this.profiles = profiles; this.users = users; this.audit = audit;
    }

    @PreAuthorize("hasRole('STUDENT') and hasAuthority('portal:academic:access') and #userId == authentication.principal.id")
    public StudentProfileView getMyProfile(UUID userId) {
        var profile = profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new);
        var user = profile.getUser();
        return new StudentProfileView(profile.getId(), user.getId(), profile.getStudentCode(),
                user.getFullName(), user.getEmail(), profile.getAdmissionDate(), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:students:manage')")
    public StudentProfileView getForUser(UUID userId) {
        return view(profiles.findByUser_Id(userId).orElseThrow(StudentProfileNotFoundException::new));
    }

    /** Crea el perfil de una cuenta estudiante o corrige su código; sin perfil no puede matricularse. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('academic:students:manage')")
    public StudentProfileView saveForUser(UUID userId, String studentCode, LocalDate admissionDate) {
        var user = users.findById(userId).orElseThrow(() ->
                new AcademicException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
        if (user.getRoles().stream().noneMatch(role -> role.getName() == RoleName.ROLE_STUDENT)) {
            throw new AcademicException(HttpStatus.CONFLICT, "NOT_A_STUDENT", "Solo las cuentas con rol Estudiante tienen perfil académico.");
        }
        var code = StudentProfile.normalizeCode(studentCode);
        var current = profiles.findByUser_Id(userId);
        profiles.findByStudentCode(code).filter(other -> current.map(p -> !p.getId().equals(other.getId())).orElse(true)).ifPresent(other -> {
            throw new AcademicException(HttpStatus.CONFLICT, "STUDENT_CODE_EXISTS", "Ese código de estudiante ya pertenece a otra cuenta.");
        });
        StudentProfile profile;
        if (current.isPresent()) {
            profile = current.get();
            profile.revise(code, admissionDate);
        } else {
            profile = new StudentProfile(user, code, admissionDate);
        }
        var saved = profiles.saveAndFlush(profile);
        audit.record(AuditAction.STUDENT_PROFILE_SAVED, "USER", userId.toString(), "código=" + saved.getStudentCode());
        return view(saved);
    }

    private static StudentProfileView view(StudentProfile profile) {
        var user = profile.getUser();
        return new StudentProfileView(profile.getId(), user.getId(), profile.getStudentCode(),
                user.getFullName(), user.getEmail(), profile.getAdmissionDate(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
