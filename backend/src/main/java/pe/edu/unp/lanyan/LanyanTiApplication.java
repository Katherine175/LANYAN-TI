package pe.edu.unp.lanyan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LanyanTiApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(LanyanTiApplication.class, args);
        if (context.getEnvironment().acceptsProfiles(org.springframework.core.env.Profiles.of("bootstrap-admin"))) {
            context.close();
        }
    }
}
