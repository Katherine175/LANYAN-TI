-- Fase 2.1: perfil propio. No crea usuarios ni datos académicos ficticios.
CREATE TABLE academic_student_profiles (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    student_code varchar(32) NOT NULL,
    admission_date date,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_academic_student_profiles_user FOREIGN KEY (user_id) REFERENCES identity_users(id),
    CONSTRAINT uq_academic_student_profiles_user UNIQUE (user_id),
    CONSTRAINT uq_academic_student_profiles_code UNIQUE (student_code),
    CONSTRAINT ck_academic_student_profiles_code CHECK (
        length(student_code) BETWEEN 1 AND 32 AND student_code = upper(trim(student_code))
    )
);
