package pe.edu.unp.lanyan.identity.application;

import java.util.Set;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.unp.lanyan.audit.application.AuditService;
import pe.edu.unp.lanyan.audit.domain.AuditAction;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.CreateUserRequest;
import pe.edu.unp.lanyan.identity.domain.RoleName;
import pe.edu.unp.lanyan.identity.domain.UserAccount;
import pe.edu.unp.lanyan.identity.infrastructure.RoleRepository;
import pe.edu.unp.lanyan.identity.infrastructure.UserAccountRepository;

@Service
public class BootstrapAdminProvisioner {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public BootstrapAdminProvisioner(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwords,
            Validator validator, JdbcTemplate jdbc, AuditService audit) {
        this.users = users; this.roles = roles; this.passwords = passwords; this.validator = validator; this.jdbc = jdbc; this.audit = audit;
    }

    @Transactional
    public void provision(String fullName, String email, String password) {
        jdbc.execute("SELECT pg_advisory_xact_lock(764202601)");
        if (users.count() != 0) throw new IllegalStateException("La base ya tiene usuarios; utiliza una cuenta administradora existente.");
        var request = new CreateUserRequest(fullName, email, password, Set.of(RoleName.ROLE_ADMIN));
        if (!validator.validate(request).isEmpty()) throw new IllegalArgumentException("Datos de administrador inicial inválidos.");
        PasswordPolicy.validateNewPassword(password);
        InstitutionalEmail.require(UserAccount.normalizeEmail(email));
        var adminRoles = roles.findAllByNameIn(request.roles());
        if (adminRoles.size() != 1) throw new IllegalStateException("No se encuentra el rol administrador; revisa las migraciones.");
        var admin = users.saveAndFlush(new UserAccount(fullName, email, passwords.encode(password), adminRoles));
        audit.record(AuditAction.ADMIN_BOOTSTRAPPED, "USER", admin.getId().toString(), null);
    }
}
