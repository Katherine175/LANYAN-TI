package pe.edu.unp.lanyan.requests.domain;

/** Validación común de textos libres de los flujos de solicitud. */
public final class Texts {
    private Texts() { }

    public static String required(String value, int max, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        var trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("El texto admite hasta " + max + " caracteres.");
        return trimmed;
    }
}
