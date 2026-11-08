package pe.edu.unp.lanyan.shared.application;

import org.springframework.http.HttpStatus;

/** Error de negocio con estado HTTP y código estable para el frontend. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
