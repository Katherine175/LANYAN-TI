package pe.edu.unp.lanyan.academic.domain;

import java.util.Locale;

public final class AcademicRules {
    private AcademicRules() { }

    public static String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException("Texto obligatorio vacío o demasiado largo.");
        }
        return value.trim();
    }

    public static String code(String value, int maxLength) {
        return requiredText(requiredText(value, maxLength).toUpperCase(Locale.ROOT), maxLength);
    }

    public static String optionalText(String value, int maxLength) {
        return value == null || value.isBlank() ? null : requiredText(value, maxLength);
    }
}
