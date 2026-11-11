-- El estudiante se matricula solo desde el catálogo; el administrador registra su código de estudiante.
INSERT INTO identity_permissions (name, description) VALUES
    ('academic:enrollments:create:self', 'Matricularse en una sección abierta'),
    ('academic:students:manage', 'Registrar y corregir el perfil académico de los estudiantes');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_STUDENT', 'academic:enrollments:create:self'),
    ('ROLE_ADMIN', 'academic:students:manage');
