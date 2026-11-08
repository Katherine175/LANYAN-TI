package pe.edu.unp.lanyan.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {
    private record Window(Instant end, int attempts) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int limit;
    private final Duration window;

    public LoginAttemptLimiter(Clock clock, @Value("${lanyan.auth.login-limit:20}") int limit,
            @Value("${lanyan.auth.login-window:5m}") Duration window) {
        if (limit < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Límite de login inválido.");
        this.clock = clock;
        this.limit = limit;
        this.window = window;
    }

    public synchronized void check(String address) {
        var now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().end().isAfter(now));
        var current = windows.get(address);
        if ((current == null && windows.size() >= 10000) || (current != null && current.attempts() >= limit)) {
            throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMIT", "Demasiados intentos. Inténtalo más tarde.");
        }
        windows.put(address, current == null ? new Window(now.plus(window), 1)
                : new Window(current.end(), current.attempts() + 1));
    }
}
