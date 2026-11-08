package pe.edu.unp.lanyan.audit.domain;

public enum AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGOUT,
    ADMIN_BOOTSTRAPPED,
    USER_CREATED,
    USER_ROLES_CHANGED
}
