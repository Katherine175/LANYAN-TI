package pe.edu.unp.lanyan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.unp.lanyan.identity.api.IdentityDtos.CreateUserRequest;
import pe.edu.unp.lanyan.identity.application.*;
import pe.edu.unp.lanyan.identity.domain.*;
import pe.edu.unp.lanyan.identity.infrastructure.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class IdentityRulesTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "            "})
    void weakPasswordsAreRejected(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validateNewPassword(password)).isInstanceOf(IdentityException.class);
    }

    @Test
    void passwordLimitUsesUtf8BytesWithoutSilentTruncation() {
        assertThatCode(() -> PasswordPolicy.validateNewPassword("é".repeat(36))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validateNewPassword("é".repeat(37))).isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> PasswordPolicy.validateNewPassword("a".repeat(73))).isInstanceOf(IdentityException.class);
    }

    @Test
    void immutableRoleSetsAreAcceptedAndEmailIsNormalized() {
        var users = mock(UserAccountRepository.class);
        var roles = mock(RoleRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.ROLE_STUDENT);
        when(role.getPermissions()).thenReturn(Set.of());
        when(roles.findAllByNameIn(Set.of(RoleName.ROLE_STUDENT))).thenReturn(Set.of(role));
        when(encoder.encode(any())).thenReturn("encoded-test-hash");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new UserService(users, roles, encoder, mock(JdbcTemplate.class));
        var view = service.create(new CreateUserRequest("Persona FICTICIA", "STUDENT@EXAMPLE.TEST",
                "Fictitious-Password42!", Set.of(RoleName.ROLE_STUDENT)));
        assertThat(view.email()).isEqualTo("student@example.test");
        assertThat(view.roles()).containsExactly(RoleName.ROLE_STUDENT);
    }

    @Test
    void loginRateLimitExpiresAtTheWindowBoundary() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock, 2, Duration.ofMinutes(1));
        limiter.check("test-address");
        limiter.check("test-address");
        assertThatThrownBy(() -> limiter.check("test-address")).isInstanceOf(IdentityException.class);
        assertThatCode(() -> limiter.check("other-address")).doesNotThrowAnyException();
        clock.now = clock.now.plusSeconds(60);
        assertThatCode(() -> limiter.check("test-address")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
