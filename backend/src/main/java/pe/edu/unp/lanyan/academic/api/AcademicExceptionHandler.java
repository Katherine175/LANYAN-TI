package pe.edu.unp.lanyan.academic.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.unp.lanyan.academic.application.StudentProfileNotFoundException;
import pe.edu.unp.lanyan.academic.application.AcademicException;

@RestControllerAdvice(assignableTypes = {StudentProfileController.class, StudentCoursesController.class,
        AcademicAdminController.class, AcademicContentAdminController.class, StudentAcademicContentController.class})
public class AcademicExceptionHandler {
    @ExceptionHandler(AcademicException.class)
    ProblemDetail academic(AcademicException ex) {
        var problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidAcademicData(IllegalArgumentException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los datos académicos enviados.");
        problem.setProperty("code", "INVALID_ACADEMIC_DATA");
        return problem;
    }

    @ExceptionHandler(StudentProfileNotFoundException.class)
    ProblemDetail profileNotFound(StudentProfileNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setProperty("code", "STUDENT_PROFILE_NOT_FOUND");
        return problem;
    }
}
