package pe.edu.unp.lanyan.identity.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.edu.unp.lanyan.identity.application.BootstrapAdminProvisioner;

@Component
@Profile("bootstrap-admin")
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminProvisioner provisioner;
    private final Environment environment;
    public BootstrapAdminRunner(BootstrapAdminProvisioner provisioner, Environment environment) {
        this.provisioner = provisioner; this.environment = environment;
    }
    @Override
    public void run(ApplicationArguments args) {
        provisioner.provision(environment.getRequiredProperty("LANYAN_ADMIN_NAME"),
                environment.getRequiredProperty("LANYAN_ADMIN_EMAIL"), environment.getRequiredProperty("LANYAN_ADMIN_PASSWORD"));
    }
}
