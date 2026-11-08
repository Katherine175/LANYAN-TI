package pe.edu.unp.lanyan.identity.api;

import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.edu.unp.lanyan.identity.application.IdentityException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    }

    @ExceptionHandler(IdentityException.class)
    ProblemDetail identity(IdentityException ex) { return problem(ex.status(), ex.code(), ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        var result = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisa los datos enviados.");
        var fields = new TreeMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        result.setProperty("errors", fields);
        return result;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail malformed(Exception ex) { return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Formato de solicitud inválido."); }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception ex) { return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "La operación entra en conflicto con los datos actuales."); }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
