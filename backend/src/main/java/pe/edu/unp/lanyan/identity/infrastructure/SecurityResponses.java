package pe.edu.unp.lanyan.identity.infrastructure;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

public final class SecurityResponses {
    private SecurityResponses() { }

    /** Solo recibe códigos constantes del servidor, nunca contenido de la petición. */
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }
}
