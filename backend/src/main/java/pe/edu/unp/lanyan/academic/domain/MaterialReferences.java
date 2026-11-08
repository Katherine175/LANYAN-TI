package pe.edu.unp.lanyan.academic.domain;

import java.net.URI;
import java.util.Locale;

public final class MaterialReferences {
    private MaterialReferences() { }

    // Pure syntax validation. Never performs DNS, HTTP or filesystem access.
    public static String normalize(MaterialReferenceType type, String value) {
        if (type == null || value == null || value.isBlank() || value.length() > 2048
                || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || value.contains("\\")) throw invalid();
        if (type == MaterialReferenceType.LOCAL_REFERENCE) {
            if (value.length() > 1024 || value.contains("..")
                    || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*")) throw invalid();
            return value.toLowerCase(Locale.ROOT);
        }
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || host == null || uri.getPort() != -1 && uri.getPort() != 443) throw invalid();
            host = host.toLowerCase(Locale.ROOT);
            if (host.length() > 253 || !host.contains(".") || host.matches("[0-9.]+")
                    || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")
                    || host.endsWith(".") || host.contains(":")) throw invalid();
            for (String label : host.split("\\.", -1)) {
                if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw invalid();
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.matches(".*%(?:0[0-9a-f]|1[0-9a-f]|7f).*")) throw invalid();
            String path = uri.getRawPath();
            String normalized = new URI("https://" + host + (path == null || path.isEmpty() ? "/" : path)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment())).normalize().toASCIIString();
            if (normalized.length() > 2048) throw invalid();
            return normalized;
        } catch (java.net.URISyntaxException ex) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Referencia educativa invalida.");
    }
}
