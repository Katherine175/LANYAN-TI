package pe.edu.unp.lanyan.identity.application;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validateNewPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "La contraseña debe tener al menos 12 caracteres y un máximo de 72 bytes UTF-8.");
        }
    }
}
