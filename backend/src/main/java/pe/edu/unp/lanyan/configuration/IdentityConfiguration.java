package pe.edu.unp.lanyan.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class IdentityConfiguration {
    @Bean
    Clock identityClock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
