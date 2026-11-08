package pe.edu.unp.lanyan.academic.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

final class AcademicPages {
    private AcademicPages() { }
    static PageRequest request(int page, int size, String dateField) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AcademicException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Usa page >= 0 y un tamaño entre 1 y 100.");
        }
        return PageRequest.of(page, size, Sort.by(dateField).descending().and(Sort.by("id")));
    }
}
