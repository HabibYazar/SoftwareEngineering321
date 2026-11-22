-- =============================================================================
-- HMS Community | Flyway migration V3
-- Patient registration and permitted updates.
--
-- Stories: US-06 Register patient (#175, data #177)
--          US-08 Update patient information (#193, data #196)
--
-- Rules implemented in the database
--   * Required: first name, last name, national ID (11 digits, synthetic),
--     date of birth, gender, phone, address, condition level.
--   * Condition level: MILD, MODERATE or ADVANCED. ADVANCED requires relative
--     information (full name, relationship, phone); for MILD/MODERATE it is
--     optional. Raising a patient to ADVANCED without a relative cannot be saved.
--   * Blank (NULL, '' or whitespace) or invalid values are rejected with
--     SQLSTATE HV001 before the row is written; COLUMN / DATATYPE diagnostic
--     fields name the first failing column, DETAIL lists all field errors.
--   * A second patient with the same national ID is rejected (field error
--     DUPLICATE, unique index uq_patients_national_id as a backstop).
--   * Every field except the national ID can be updated (US-08); the same
--     validation applies; each update is logged with before/after values.
--     Administrators may also correct the national ID.
--   * Receptionists and administrators may register and update patients.
--   * Administrators can delete a patient completely (patient + relative
--     information) at any time with patient_delete(:id, :reason). The reason is
--     mandatory and logged; the deleted values are not copied into the log.
--
-- Optional relative information is a separate 1:1 table: "no relative" = no row.
-- =============================================================================

SELECT set_config('hms.trusted_op', 'migration', false);

CREATE TABLE patients (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    national_id     text        NOT NULL,
    first_name      text        NOT NULL,
    last_name       text        NOT NULL,
    date_of_birth   date        NOT NULL,
    gender          text        NOT NULL,
    phone           text        NOT NULL,
    address         text        NOT NULL,
    condition_level text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_patients_national_id UNIQUE (national_id),
    CONSTRAINT ck_patients_national_id_format     CHECK (national_id ~ '^[0-9]{11}$'),
    CONSTRAINT ck_patients_first_name_not_blank   CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_patients_last_name_not_blank    CHECK (btrim(last_name) <> ''),
    CONSTRAINT ck_patients_gender_not_blank       CHECK (btrim(gender) <> ''),
    CONSTRAINT ck_patients_phone_not_blank        CHECK (btrim(phone) <> ''),
    CONSTRAINT ck_patients_address_not_blank      CHECK (btrim(address) <> ''),
    CONSTRAINT ck_patients_date_of_birth_min      CHECK (date_of_birth >= DATE '1900-01-01'),
    CONSTRAINT ck_patients_condition_level        CHECK (condition_level IN ('MILD', 'MODERATE', 'ADVANCED'))
);

-- OpenAPI RelativeInformation {fullName, relationship, phone}; one per patient.
CREATE TABLE patient_relatives (
    patient_id   uuid        PRIMARY KEY REFERENCES patients (id) ON DELETE CASCADE,
    full_name    text        NOT NULL,
    relationship text        NOT NULL,
    phone        text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_patient_relatives_full_name_not_blank    CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_patient_relatives_relationship_not_blank CHECK (btrim(relationship) <> ''),
    CONSTRAINT ck_patient_relatives_phone_not_blank        CHECK (btrim(phone) <> '')
);

-- -----------------------------------------------------------------------------
-- Validation rules (single source of truth for blank / invalid detection)
-- -----------------------------------------------------------------------------
INSERT INTO field_rules (table_name, column_name, api_field, data_type, label_tr, label_en, required,
                         max_length, pattern, allowed_values, min_value, max_value, unique_mode, immutable, sort_order) VALUES
('patients', 'national_id',     'nationalId',     'text', 'TC kimlik numarası', 'National ID',     true, 11,   '^[0-9]{11}$', NULL, NULL, NULL, 'exact', true, 1),
('patients', 'first_name',      'firstName',      'text', 'Ad',                 'First name',      true, 100,  NULL, NULL, NULL, NULL, NULL, false, 2),
('patients', 'last_name',       'lastName',       'text', 'Soyad',              'Last name',       true, 100,  NULL, NULL, NULL, NULL, NULL, false, 3),
('patients', 'date_of_birth',   'dateOfBirth',    'date', 'Doğum tarihi',       'Date of birth',   true, NULL, NULL, NULL, '1900-01-01', 'today', NULL, false, 4),
('patients', 'gender',          'gender',         'text', 'Cinsiyet',           'Gender',          true, 40,   NULL, NULL, NULL, NULL, NULL, false, 5),
('patients', 'phone',           'phone',          'text', 'Telefon',            'Phone',           true, 32,   '^\+?[0-9 ()-]{7,31}$', NULL, NULL, NULL, NULL, false, 6),
('patients', 'address',         'address',        'text', 'Adres',              'Address',         true, 500,  NULL, NULL, NULL, NULL, NULL, false, 7),
('patients', 'condition_level', 'conditionLevel', 'text', 'Durum seviyesi',     'Condition level', true, NULL, NULL, '{MILD,MODERATE,ADVANCED}', NULL, NULL, NULL, false, 8),
('patient_relatives', 'patient_id',   'patientId',                        'uuid', 'Hasta',              'Patient',            true, NULL, NULL, NULL, NULL, NULL, NULL, true,  1),
('patient_relatives', 'full_name',    'relativeInformation.fullName',     'text', 'Yakının adı soyadı', 'Relative full name', true, 150,  NULL, NULL, NULL, NULL, NULL, false, 2),
('patient_relatives', 'relationship', 'relativeInformation.relationship', 'text', 'Yakınlık derecesi',  'Relationship',       true, 80,   NULL, NULL, NULL, NULL, NULL, false, 3),
('patient_relatives', 'phone',        'relativeInformation.phone',        'text', 'Yakının telefonu',   'Relative phone',     true, 32,   '^\+?[0-9 ()-]{7,31}$', NULL, NULL, NULL, NULL, false, 4);

-- -----------------------------------------------------------------------------
-- Rules
-- -----------------------------------------------------------------------------
-- Relative rows may be added during registration (PATIENT_CREATE) or later (PATIENT_UPDATE).
CREATE FUNCTION patient_relatives_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid;
BEGIN
    IF hms_trusted_op() = '' THEN
        v_actor := hms_require_actor();
        IF NOT (has_permission(v_actor, 'PATIENT_UPDATE')
                OR (TG_OP = 'INSERT' AND has_permission(v_actor, 'PATIENT_CREATE'))) THEN
            RAISE EXCEPTION USING
                ERRCODE = 'HA403',
                MESSAGE = 'PERMISSION_DENIED: PATIENT_UPDATE',
                DETAIL  = 'PATIENT_UPDATE';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

-- Deferred: checked at COMMIT (or at SET CONSTRAINTS ... IMMEDIATE), so a patient
-- and its relative can be inserted in either order inside one transaction.
CREATE FUNCTION patients_require_relative() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_patient uuid;
BEGIN
    IF TG_TABLE_NAME = 'patients' THEN
        v_patient := NEW.id;
    ELSE
        v_patient := OLD.patient_id;
    END IF;
    IF EXISTS (SELECT 1 FROM patients p WHERE p.id = v_patient AND p.condition_level = 'ADVANCED')
       AND NOT EXISTS (SELECT 1 FROM patient_relatives r WHERE r.patient_id = v_patient) THEN
        PERFORM hms_raise_validation('patients', (
            SELECT jsonb_build_array(jsonb_build_object(
                       'field',     'relativeInformation',
                       'column',    'relative_information',
                       'dataType',  'record',
                       'error',     'RELATIVE_REQUIRED',
                       'message',   format(e.message_tr, 'Yakın bilgisi (ad soyad, yakınlık derecesi, telefon)', ''),
                       'messageEn', format(e.message_en, 'Relative information (full name, relationship, phone)', '')))
            FROM validation_error_codes e
            WHERE e.code = 'RELATIVE_REQUIRED'));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_10_patients_guard BEFORE INSERT OR UPDATE OR DELETE ON patients
    FOR EACH ROW EXECUTE FUNCTION hms_guard('PATIENT_CREATE', 'PATIENT_UPDATE', 'PATIENT_DELETE', 'REASON');
CREATE TRIGGER trg_30_patients_validate BEFORE INSERT OR UPDATE ON patients
    FOR EACH ROW EXECUTE FUNCTION hms_validate_row();
CREATE TRIGGER trg_40_patients_touch BEFORE INSERT OR UPDATE ON patients
    FOR EACH ROW EXECUTE FUNCTION hms_touch();
CREATE CONSTRAINT TRIGGER trg_60_patients_relative_required
    AFTER INSERT OR UPDATE OF condition_level ON patients
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION patients_require_relative();
CREATE TRIGGER trg_90_patients_audit AFTER INSERT OR UPDATE OR DELETE ON patients
    FOR EACH ROW EXECUTE FUNCTION audit_row_change('PATIENT', 'id', 'created_at,updated_at,version');
CREATE TRIGGER trg_95_patients_deletion AFTER DELETE ON patients
    FOR EACH ROW EXECUTE FUNCTION hms_record_deletion('PATIENT');

CREATE TRIGGER trg_10_patient_relatives_guard BEFORE INSERT OR UPDATE OR DELETE ON patient_relatives
    FOR EACH ROW EXECUTE FUNCTION patient_relatives_guard();
CREATE TRIGGER trg_30_patient_relatives_validate BEFORE INSERT OR UPDATE ON patient_relatives
    FOR EACH ROW EXECUTE FUNCTION hms_validate_row();
CREATE TRIGGER trg_40_patient_relatives_touch BEFORE INSERT OR UPDATE ON patient_relatives
    FOR EACH ROW EXECUTE FUNCTION hms_touch();
CREATE CONSTRAINT TRIGGER trg_60_patient_relatives_required
    AFTER DELETE ON patient_relatives
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION patients_require_relative();
CREATE TRIGGER trg_90_patient_relatives_audit AFTER INSERT OR UPDATE OR DELETE ON patient_relatives
    FOR EACH ROW EXECUTE FUNCTION audit_row_change('PATIENT_RELATIVE', 'patient_id', 'created_at,updated_at,version');

-- Complete deletion by an administrator: patient + relative information.
-- The reason is mandatory (HV001 deleteReason otherwise) and is logged.
-- Returns false when the patient does not exist.
CREATE FUNCTION patient_delete(p_patient_id uuid, p_reason text) RETURNS boolean
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_count integer;
BEGIN
    PERFORM hms_set_delete_reason(p_reason);
    DELETE FROM patients WHERE id = p_patient_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    PERFORM hms_set_delete_reason(NULL);
    RETURN v_count > 0;
END
$$;

-- -----------------------------------------------------------------------------
-- Permissions: Administrator automatically (including PATIENT_DELETE);
-- Receptionist registers, views and updates patients (US-06, US-08).
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, module, story, description_tr, description_en) VALUES
(5, 'PATIENT_CREATE', 'patients', 'US-06',        'Hasta kaydı oluşturma',            'Register patients'),
(6, 'PATIENT_VIEW',   'patients', 'US-06, US-08', 'Hasta bilgilerini görüntüleme',    'View patient information'),
(7, 'PATIENT_UPDATE', 'patients', 'US-08',        'Hasta bilgilerini güncelleme',     'Update patient information'),
(9, 'PATIENT_DELETE', 'patients', 'US-08',        'Hasta kaydını silme nedeniyle komple silme', 'Delete a patient record completely, with a reason');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('PATIENT_CREATE', 'PATIENT_VIEW', 'PATIENT_UPDATE')
WHERE r.code = 'Receptionist';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hms_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON patients TO hms_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON patient_relatives TO hms_app;
    END IF;
END
$$;

SELECT set_config('hms.trusted_op', '', false);
