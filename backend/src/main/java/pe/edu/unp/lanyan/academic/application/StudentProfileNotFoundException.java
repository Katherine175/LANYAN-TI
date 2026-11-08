package pe.edu.unp.lanyan.academic.application;

public class StudentProfileNotFoundException extends RuntimeException {
    public StudentProfileNotFoundException() {
        super("Todavía no existe un perfil estudiantil para tu cuenta.");
    }
}
