CREATE TABLE academic_class_schedules (
    id uuid PRIMARY KEY,
    section_id uuid NOT NULL REFERENCES academic_course_sections(id),
    day_of_week integer NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time time(0) NOT NULL,
    end_time time(0) NOT NULL,
    modality varchar(32) NOT NULL CHECK (modality IN ('IN_PERSON','ONLINE','HYBRID')),
    classroom varchar(120),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_schedule_times CHECK (
        start_time < end_time AND EXTRACT(SECOND FROM start_time)=0 AND EXTRACT(SECOND FROM end_time)=0
        AND end_time < TIME '24:00'),
    CONSTRAINT ck_schedule_room CHECK (
        (classroom IS NULL OR length(trim(classroom)) > 0)
        AND (modality='ONLINE' OR classroom IS NOT NULL))
);
CREATE UNIQUE INDEX uq_schedule_active_slot ON academic_class_schedules(section_id,day_of_week,start_time,end_time)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_schedule_section_day ON academic_class_schedules(section_id,day_of_week,start_time)
    WHERE deleted_at IS NULL;

CREATE TABLE academic_course_materials (
    id uuid PRIMARY KEY,
    section_id uuid NOT NULL REFERENCES academic_course_sections(id),
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    description varchar(2000),
    type varchar(32) NOT NULL CHECK (type IN ('DOCUMENT','GUIDE','LINK')),
    reference_type varchar(32) NOT NULL CHECK (reference_type IN ('LOCAL_REFERENCE','HTTPS_URL')),
    reference varchar(2048) NOT NULL CHECK (length(trim(reference)) > 0),
    status varchar(32) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_material_link CHECK (type <> 'LINK' OR reference_type='HTTPS_URL'),
    CONSTRAINT ck_material_reference CHECK (
        (reference_type='HTTPS_URL' AND reference LIKE 'https://%')
        OR (reference_type='LOCAL_REFERENCE' AND length(reference)<=1024
            AND reference ~ '^[a-z0-9][a-z0-9._-]*(/[a-z0-9][a-z0-9._-]*)*$'
            AND position('..' in reference)=0))
);
CREATE UNIQUE INDEX uq_material_active_reference ON academic_course_materials(section_id,reference_type,reference)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_material_section_status ON academic_course_materials(section_id,status)
    WHERE deleted_at IS NULL;

-- Access capabilities only. No academic or identity fixture data is inserted.
INSERT INTO identity_permissions (name, description) VALUES
    ('academic:schedules:manage', 'Administrar horarios por seccion'),
    ('academic:materials:manage', 'Administrar referencias de materiales por seccion'),
    ('academic:schedules:read:self', 'Consultar horarios de las matriculas propias'),
    ('academic:materials:read:self', 'Consultar materiales publicados de las matriculas propias');
INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN','academic:schedules:manage'),
    ('ROLE_ADMIN','academic:materials:manage'),
    ('ROLE_STUDENT','academic:schedules:read:self'),
    ('ROLE_STUDENT','academic:materials:read:self');
