package pe.edu.unp.lanyan.academic.application;

import org.springframework.http.HttpStatus;
import pe.edu.unp.lanyan.shared.application.ApiException;

public class AcademicException extends ApiException {
    public AcademicException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
