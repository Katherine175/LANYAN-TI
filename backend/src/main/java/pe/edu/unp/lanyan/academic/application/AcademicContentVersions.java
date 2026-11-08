package pe.edu.unp.lanyan.academic.application;

import org.springframework.http.HttpStatus;

final class AcademicContentVersions {
    private AcademicContentVersions() { }
    static void requireMatches(Long current, Long expected) {
        if (expected == null || expected < 0) throw new AcademicException(
                HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Envia la version actual del registro.");
        if (!expected.equals(current)) throw new AcademicException(
                HttpStatus.CONFLICT, "STALE_RESOURCE", "El registro cambio; vuelve a consultarlo.");
    }
}
