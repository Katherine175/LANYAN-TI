package pe.edu.unp.lanyan.identity.application;

import org.springframework.http.HttpStatus;

/** Las cuentas nuevas solo se crean con correo de la Universidad Nacional de Piura. */
public final class InstitutionalEmail {
    public static final String DOMAIN = "unp.edu.pe";

    private InstitutionalEmail() { }

    /** Recibe el correo ya normalizado (sin espacios y en minúsculas). */
    public static boolean accepts(String email) {
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && email.substring(at + 1).equals(DOMAIN);
    }

    public static void require(String email) {
        if (!accepts(email)) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "EMAIL_DOMAIN_NOT_ALLOWED",
                    "Usa un correo institucional que termine en @" + DOMAIN + ".");
        }
    }
}
