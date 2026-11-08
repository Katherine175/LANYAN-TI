CREATE TABLE academic_courses (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE,
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0),
    language varchar(80) NOT NULL CHECK (length(trim(language)) > 0),
    level varchar(80),
    description varchar(2000),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_academic_courses_code CHECK (length(code) BETWEEN 1 AND 32 AND code = upper(trim(code)))
);

CREATE TABLE academic_course_sections (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES academic_courses(id),
    code varchar(32) NOT NULL,
    academic_period varchar(40) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0),
    status varchar(32) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_academic_section_course_period_code UNIQUE (course_id, academic_period, code),
    CONSTRAINT ck_academic_section_code CHECK (length(code) BETWEEN 1 AND 32 AND code = upper(trim(code))),
    CONSTRAINT ck_academic_section_period CHECK (length(academic_period) BETWEEN 1 AND 40 AND academic_period = upper(trim(academic_period))),
    CONSTRAINT ck_academic_section_dates CHECK (end_date >= start_date)
);

CREATE TABLE academic_enrollments (
    id uuid PRIMARY KEY,
    student_profile_id uuid NOT NULL REFERENCES academic_student_profiles(id),
    section_id uuid NOT NULL REFERENCES academic_course_sections(id),
    status varchar(32) NOT NULL DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED','CANCELLED','COMPLETED')),
    enrolled_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_academic_enrollment_student_section UNIQUE (student_profile_id, section_id)
);
CREATE INDEX idx_academic_enrollment_section_status ON academic_enrollments(section_id, status);

-- Solo capacidades de acceso: no se insertan cursos, secciones, matrículas ni usuarios.
INSERT INTO identity_permissions (name, description) VALUES
    ('academic:read', 'Consultar cursos, secciones y matrículas desde administración'),
    ('academic:courses:manage', 'Registrar cursos académicos'),
    ('academic:sections:manage', 'Registrar secciones académicas'),
    ('academic:enrollments:manage', 'Matricular estudiantes de forma controlada'),
    ('academic:enrollments:read:self', 'Consultar exclusivamente las matrículas propias');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN', 'academic:read'),
    ('ROLE_ADMIN', 'academic:courses:manage'),
    ('ROLE_ADMIN', 'academic:sections:manage'),
    ('ROLE_ADMIN', 'academic:enrollments:manage'),
    ('ROLE_STUDENT', 'academic:enrollments:read:self');
