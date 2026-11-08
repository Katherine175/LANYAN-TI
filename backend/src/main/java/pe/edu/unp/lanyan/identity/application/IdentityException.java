package pe.edu.unp.lanyan.identity.application;

import org.springframework.http.HttpStatus;
import pe.edu.unp.lanyan.shared.application.ApiException;

public class IdentityException extends ApiException {
    public IdentityException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
