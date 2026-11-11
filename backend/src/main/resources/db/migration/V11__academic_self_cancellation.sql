-- El estudiante anula su propia matrícula antes de que empiece la sección.
INSERT INTO identity_permissions (name, description) VALUES
    ('academic:enrollments:cancel:self', 'Anular la propia matrícula antes del inicio de la sección');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_STUDENT', 'academic:enrollments:cancel:self');
