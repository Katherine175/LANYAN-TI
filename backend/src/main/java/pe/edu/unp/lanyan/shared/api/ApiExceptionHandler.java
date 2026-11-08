package pe.edu.unp.lanyan.shared.api;

import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.edu.unp.lanyan.shared.application.ApiException;

/** Formato único de errores. Va al final para que los manejadores de cada módulo tengan prioridad. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail business(ApiException ex) { return problem(ex.status(), ex.code(), ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        var result = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisa los datos enviados.");
        var fields = new TreeMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        result.setProperty("errors", fields);
        return result;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HandlerMethodValidationException.class})
    ProblemDetail malformed(Exception ex) { return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Formato de solicitud inválido."); }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception ex) { return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "La operación entra en conflicto con los datos actuales."); }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail notFound(NoResourceFoundException ex) { return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe."); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Operación no disponible en esta ruta.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Formato de contenido no admitido.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail notAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return problem(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Formato de respuesta no admitido.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) throws Exception {
        // Los rechazos de @PreAuthorize los resuelve Spring Security como 401/403.
        if (ex instanceof AccessDeniedException) throw ex;
        log.error("Error no controlado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error interno. Inténtalo más tarde.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
