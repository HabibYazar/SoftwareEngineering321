-- =============================================================================
-- HMS Community | Flyway migration V1
-- Security & staff core: roles, permissions, role-permission matrix,
-- departments, staff members, doctor-department links, user accounts,
-- sessions, and the field-validation framework shared by every module.
--
-- Stories: US-01 Staff login (#107, data #114)
--          US-02 Role-based access control (#130) - role/permission data
--          US-05 Manage staff accounts and permissions (#163, data #165)
--
-- Data policy: this migration inserts NO demo, synthetic or real person data.
-- It only inserts reference data the stories define as part of the system:
-- the seven US-02 roles, the permission catalogue, the initial role matrix,
-- validation/error message catalogues. The first administrator is created at
-- deployment time with security_bootstrap_admin(...) (docs/database/README.md).
--
-- Conventions
--   * A value that is not provided is stored as NULL - never as '' or 0.
--     Required columns are NOT NULL; an empty / whitespace-only value sent for
--     an optional column is stored as NULL. Real numbers keep their value
--     (failed_login_count = 0 means "no failed attempt", version is JPA's).
--   * Blank or invalid fields are rejected before the row is written with
--     SQLSTATE 'HV001'. The error carries the column name and its data type in
--     the standard COLUMN / DATATYPE diagnostic fields, and DETAIL holds a JSON
--     array with every failing field (see hms_field_errors()).
--   * Every write to a business table needs the acting user in the
--     transaction context: SELECT hms_set_context(:accountId, :sessionId);
--   * Administrators can do everything: they hold every permission, may change
--     fields that are immutable for other roles (national ID, username) and
--     may delete patients, staff and accounts; a hard delete needs a reason
--     (hms_set_delete_reason / patient_delete / staff_delete), which is logged.
--     Still enforced for administrators: the audit log is immutable (US-03),
--     an administrator cannot deactivate or delete their own account, and the
--     last active administrator cannot be removed (US-05 acceptance criteria).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Runtime role for the backend (least privilege). DevOps creates the LOGIN
--    user and adds it to this role, e.g.
--    CREATE ROLE hms_backend LOGIN PASSWORD '...' IN ROLE hms_app;
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hms_app') THEN
        CREATE ROLE hms_app NOLOGIN;
    END IF;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'Role hms_app was not created (migration user lacks CREATEROLE); grants are skipped.';
END
$$;

-- Reference-data inserts below must not be blocked by the permission guards.
SELECT set_config('hms.trusted_op', 'migration', false);

-- -----------------------------------------------------------------------------
-- 1. Transaction context (set by the backend at the start of each transaction)
-- -----------------------------------------------------------------------------
CREATE FUNCTION hms_actor_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('hms.actor_id', true), '')::uuid $$;

CREATE FUNCTION hms_session_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('hms.session_id', true), '')::uuid $$;

CREATE FUNCTION hms_trusted_op() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(current_setting('hms.trusted_op', true), '') $$;

CREATE FUNCTION hms_set_context(p_actor_id uuid, p_session_id uuid DEFAULT NULL) RETURNS void
    LANGUAGE plpgsql VOLATILE
AS $$
BEGIN
    PERFORM set_config('hms.actor_id', coalesce(p_actor_id::text, ''), true);
    PERFORM set_config('hms.session_id', coalesce(p_session_id::text, ''), true);
END
$$;

-- Reason for a hard delete (patients, staff, accounts). Transaction-local; logged with the delete.
CREATE FUNCTION hms_delete_reason() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT nullif(btrim(current_setting('hms.delete_reason', true)), '') $$;

CREATE FUNCTION hms_set_delete_reason(p_reason text) RETURNS void
    LANGUAGE plpgsql VOLATILE
AS $$
BEGIN
    PERFORM set_config('hms.delete_reason', coalesce(p_reason, ''), true);
END
$$;

-- -----------------------------------------------------------------------------
-- 2. Error catalogue: SQLSTATE -> HTTP status and user-facing message
-- -----------------------------------------------------------------------------
CREATE TABLE hms_error_codes (
    sqlstate    text     PRIMARY KEY CHECK (sqlstate ~ '^[0-9A-Z]{5}$'),
    name        text     NOT NULL UNIQUE,
    http_status smallint NOT NULL CHECK (http_status BETWEEN 400 AND 599),
    message_tr  text     NOT NULL,
    message_en  text     NOT NULL,
    story       text     NOT NULL
);

INSERT INTO hms_error_codes (sqlstate, name, http_status, message_tr, message_en, story) VALUES
('HV001', 'VALIDATION_FAILED', 400,
 'Bir veya daha fazla alan boş ya da geçersiz. Ayrıntılar DETAIL alanındaki JSON listesindedir.',
 'One or more fields are blank or invalid. DETAIL holds a JSON list of field errors.', 'US-05, US-06, US-08'),
('23505', 'UNIQUE_VIOLATION', 409,
 'Bu değer başka bir kayıtta zaten kullanılıyor.', 'This value is already used by another record.', 'US-05, US-06'),
('23503', 'FOREIGN_KEY_VIOLATION', 409,
 'İlişkili kayıt bulunamadı ya da kayıt başka kayıtlar tarafından kullanılıyor.',
 'The related record does not exist or the record is still referenced.', 'US-05'),
('HA401', 'ACTOR_CONTEXT_MISSING', 401,
 'İşlemi yapan kullanıcı bu işlem için belirtilmemiş.', 'The acting user is not set for this transaction.', 'US-03'),
('HA403', 'PERMISSION_DENIED', 403,
 'Bu işlem için yetkiniz yok.', 'You are not permitted to perform this action.', 'US-02, US-05, US-08'),
('HS001', 'LAST_ADMINISTRATOR', 409,
 'Sistemdeki son aktif yönetici devre dışı bırakılamaz, silinemez ve rolü değiştirilemez.',
 'The last active administrator cannot be deactivated, deleted or lose the Administrator role.', 'US-05'),
('HS002', 'SELF_DEACTIVATION', 409,
 'Yönetici kendi hesabını devre dışı bırakamaz veya silemez.', 'Administrators cannot deactivate or delete their own account.', 'US-05'),
('HS003', 'ACCOUNT_INACTIVE', 401,
 'Hesap devre dışı; giriş yapılamaz.', 'The account is deactivated and cannot sign in.', 'US-01, US-05'),
('HS004', 'DOCTOR_ASSIGNMENT_CONFLICT', 409,
 'Departmana yalnızca Hekim rolündeki personel ve aktif departman atanabilir.',
 'Only Physician staff can be assigned, and only to an active department.', 'US-05'),
('HS005', 'ADMINISTRATOR_PERMISSIONS_LOCKED', 409,
 'Yönetici rolünün yetkileri kaldırılamaz (yönetici her zaman tüm yetkilere sahiptir).',
 'Permissions of the Administrator role cannot be removed (administrators always hold every permission).', 'US-05'),
('HS006', 'DELETE_NOT_ALLOWED', 409,
 'Bu kaydı silme yetkiniz yok; kayıtları yalnızca yönetici, silme nedeni girerek silebilir.',
 'You may not delete this record; only administrators can delete records, with a reason.', 'US-03, US-05, US-06'),
('HS007', 'SESSION_STATE_LOCKED', 409,
 'Oturum kaydı yalnızca bir kez sonlandırılabilir; başka değişiklik yapılamaz.',
 'A session can only be revoked once; no other change is allowed.', 'US-01'),
('HS008', 'BOOTSTRAP_NOT_ALLOWED', 409,
 'İlk yönetici zaten oluşturulmuş.', 'The first administrator already exists.', 'US-01');

-- -----------------------------------------------------------------------------
-- 3. Field-validation framework (blank detection with column + data type)
-- -----------------------------------------------------------------------------
CREATE TABLE validation_error_codes (
    code       text PRIMARY KEY,
    message_tr text NOT NULL,   -- format(): %1$s = field label, %2$s = limit / allowed values
    message_en text NOT NULL
);

INSERT INTO validation_error_codes (code, message_tr, message_en) VALUES
('REQUIRED',          '%1$s alanı zorunludur ve boş bırakılamaz.',       '%1$s is required and cannot be blank.'),
('TOO_LONG',          '%1$s en fazla %2$s karakter olabilir.',           '%1$s must be at most %2$s characters.'),
('INVALID_FORMAT',    '%1$s geçerli biçimde değil.',                     '%1$s has an invalid format.'),
('NOT_ALLOWED',       '%1$s için izin verilen değerler: %2$s.',          '%1$s must be one of: %2$s.'),
('OUT_OF_RANGE',      '%1$s geçerli tarih aralığında değil.',            '%1$s is outside the allowed date range.'),
('DUPLICATE',         '%1$s başka bir kayıtta zaten kullanılıyor.',      '%1$s is already used by another record.'),
('IMMUTABLE',         '%1$s kayıt oluşturulduktan sonra değiştirilemez.', '%1$s cannot be changed after the record is created.'),
('RELATIVE_REQUIRED', '%1$s durum seviyesi "İleri" olan hastalar için zorunludur.',
                      '%1$s is required for patients with an Advanced condition level.');

-- NULL in a rule column means "no such rule" (no length limit, no pattern, ...).
CREATE TABLE field_rules (
    table_name     text     NOT NULL,
    column_name    text     NOT NULL,
    api_field      text     NOT NULL,                       -- property name in REST payloads (api/openapi.yaml)
    data_type      text     NOT NULL,                       -- PostgreSQL type of the column
    label_tr       text     NOT NULL,
    label_en       text     NOT NULL,
    required       boolean  NOT NULL,
    max_length     integer  CHECK (max_length > 0),         -- NULL = no limit
    pattern        text,                                    -- NULL = no pattern
    allowed_values text[],                                  -- NULL = any value
    min_value      text,                                    -- dates: ISO date or 'today'; NULL = none
    max_value      text,
    unique_mode    text     CHECK (unique_mode IN ('exact', 'ci')),   -- NULL = not unique
    immutable      boolean  NOT NULL DEFAULT false,         -- immutable for everyone except administrators
    sort_order     smallint NOT NULL,
    PRIMARY KEY (table_name, column_name)
);

-- Returns a JSON array of field errors for a row (empty array = valid).
-- p_old is given for updates so immutable fields can be checked.
-- The backend may also call it directly to pre-validate a payload whose keys
-- are column names: SELECT hms_field_errors('patients', :json::jsonb);
CREATE FUNCTION hms_field_errors(p_table text, p_new jsonb, p_old jsonb DEFAULT NULL)
    RETURNS jsonb
    LANGUAGE plpgsql STABLE
    SET search_path = public, pg_temp
AS $$
DECLARE
    r        field_rules%ROWTYPE;
    v_value  text;
    v_date   date;
    v_code   text;
    v_extra  text;
    v_dup    boolean;
    v_admin  boolean := p_old IS NOT NULL AND hms_actor_is_admin();
    v_errors jsonb := '[]'::jsonb;
BEGIN
    FOR r IN SELECT * FROM field_rules WHERE table_name = p_table ORDER BY sort_order LOOP
        v_value := p_new ->> r.column_name;
        v_code  := NULL;
        v_extra := NULL;

        IF p_old IS NOT NULL AND r.immutable AND NOT v_admin
           AND (p_new -> r.column_name) IS DISTINCT FROM (p_old -> r.column_name) THEN
            v_code := 'IMMUTABLE';
        ELSIF v_value IS NULL OR btrim(v_value) = '' THEN
            IF r.required THEN
                v_code := 'REQUIRED';
            END IF;
        ELSIF r.max_length IS NOT NULL AND char_length(v_value) > r.max_length THEN
            v_code := 'TOO_LONG';
            v_extra := r.max_length::text;
        ELSIF r.pattern IS NOT NULL AND v_value !~ r.pattern THEN
            v_code := 'INVALID_FORMAT';
        ELSIF r.allowed_values IS NOT NULL AND NOT (v_value = ANY (r.allowed_values)) THEN
            v_code := 'NOT_ALLOWED';
            v_extra := array_to_string(r.allowed_values, ', ');
        ELSIF r.data_type = 'date' THEN
            BEGIN
                v_date := v_value::date;
                IF (r.min_value IS NOT NULL AND v_date < CASE r.min_value WHEN 'today' THEN current_date ELSE r.min_value::date END)
                   OR (r.max_value IS NOT NULL AND v_date > CASE r.max_value WHEN 'today' THEN current_date ELSE r.max_value::date END) THEN
                    v_code := 'OUT_OF_RANGE';
                END IF;
            EXCEPTION WHEN invalid_datetime_format OR datetime_field_overflow THEN
                v_code := 'INVALID_FORMAT';
            END;
        END IF;

        IF v_code IS NULL AND r.unique_mode IS NOT NULL AND v_value IS NOT NULL AND btrim(v_value) <> '' THEN
            EXECUTE format(
                'SELECT EXISTS (SELECT 1 FROM public.%I WHERE %s AND id IS DISTINCT FROM $2)',
                p_table,
                CASE r.unique_mode
                    WHEN 'ci' THEN format('lower(%I) = lower($1)', r.column_name)
                    ELSE format('%I = $1', r.column_name)
                END)
            INTO v_dup
            USING v_value, (p_new ->> 'id')::uuid;
            IF v_dup THEN
                v_code := 'DUPLICATE';
            END IF;
        END IF;

        IF v_code IS NOT NULL THEN
            v_errors := v_errors || (
                SELECT jsonb_build_object(
                    'field',     r.api_field,
                    'column',    r.column_name,
                    'dataType',  r.data_type,
                    'error',     v_code,
                    'message',   format(e.message_tr, r.label_tr, v_extra),
                    'messageEn', format(e.message_en, r.label_en, v_extra))
                FROM validation_error_codes e
                WHERE e.code = v_code);
        END IF;
    END LOOP;
    RETURN v_errors;
END
$$;

-- Raises SQLSTATE HV001 with COLUMN/DATATYPE of the first error and all errors in DETAIL.
CREATE FUNCTION hms_raise_validation(p_table text, p_errors jsonb) RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE  = 'HV001',
        MESSAGE  = format('VALIDATION_FAILED: %s.%s %s', p_table,
                          coalesce(p_errors -> 0 ->> 'column', ''), coalesce(p_errors -> 0 ->> 'error', '')),
        DETAIL   = p_errors::text,
        HINT     = coalesce(p_errors -> 0 ->> 'message', ''),
        SCHEMA   = 'public',
        TABLE    = p_table,
        COLUMN   = coalesce(p_errors -> 0 ->> 'column', ''),
        DATATYPE = coalesce(p_errors -> 0 ->> 'dataType', '');
END
$$;

CREATE FUNCTION hms_validate_row() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_errors jsonb;
    r        record;
BEGIN
    -- A value that is not provided is NULL: '' / whitespace in an optional column becomes NULL.
    FOR r IN SELECT f.column_name FROM field_rules f WHERE f.table_name = TG_TABLE_NAME AND NOT f.required LOOP
        IF btrim(to_jsonb(NEW) ->> r.column_name) = '' THEN
            NEW := jsonb_populate_record(NEW, jsonb_build_object(r.column_name, NULL));
        END IF;
    END LOOP;

    v_errors := hms_field_errors(TG_TABLE_NAME, to_jsonb(NEW),
                                 CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) END);
    IF jsonb_array_length(v_errors) > 0 THEN
        PERFORM hms_raise_validation(TG_TABLE_NAME, v_errors);
    END IF;
    RETURN NEW;
END
$$;

-- created_at is fixed at insert time; updated_at follows every update.
-- (version is left to JPA @Version so optimistic locking keeps working.)
CREATE FUNCTION hms_touch() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at := now();
    ELSE
        NEW.created_at := OLD.created_at;
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

-- -----------------------------------------------------------------------------
-- 4. Roles and permissions (US-02 seven roles, US-05 editable matrix)
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id         smallint PRIMARY KEY,
    code       text     NOT NULL UNIQUE CHECK (code ~ '^[A-Za-z]+$'),  -- = LoginResponse.role enum
    name_tr    text     NOT NULL,
    name_en    text     NOT NULL,
    sort_order smallint NOT NULL
);

INSERT INTO roles (id, code, name_tr, name_en, sort_order) VALUES
(1, 'Administrator',        'Yönetici',              'Administrator',          1),
(2, 'Receptionist',         'Resepsiyonist',         'Receptionist',           2),
(3, 'Physician',            'Hekim',                 'Physician',              3),
(4, 'Nurse',                'Hemşire',               'Nurse',                  4),
(5, 'PatientCareAssistant', 'Hasta Bakım Personeli', 'Patient Care Assistant', 5),
(6, 'LabTechnician',        'Laboratuvar Teknisyeni','Lab Technician',         6),
(7, 'CleaningStaff',        'Temizlik Personeli',    'Cleaning Staff',         7);

CREATE TABLE permissions (
    id             smallint PRIMARY KEY,
    code           text     NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    module         text     NOT NULL,
    story          text     NOT NULL,
    description_tr text     NOT NULL,
    description_en text     NOT NULL
);

CREATE TABLE role_permissions (
    role_id       smallint    NOT NULL REFERENCES roles (id),
    permission_id smallint    NOT NULL REFERENCES permissions (id),
    granted_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);

-- -----------------------------------------------------------------------------
-- 5. Departments, staff members and doctor-department links (US-05, US-09)
-- -----------------------------------------------------------------------------
CREATE TABLE departments (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL,
    is_active  boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_departments_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX uq_departments_name ON departments (lower(name));

CREATE TABLE staff_members (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id      smallint    NOT NULL REFERENCES roles (id),
    first_name   text        NOT NULL,
    last_name    text        NOT NULL,
    title        text,                              -- academic title, e.g. 'Uzm. Dr.'; NULL = none
    phone        text,                              -- NULL = not provided
    email        text,                              -- NULL = not provided
    display_name text        NOT NULL GENERATED ALWAYS AS (coalesce(title || ' ', '') || first_name || ' ' || last_name) STORED,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_members_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_staff_members_last_name_not_blank  CHECK (btrim(last_name) <> '')
);
CREATE INDEX idx_staff_members_role ON staff_members (role_id);

-- A doctor belongs to exactly one department (FR-09.1 department_id FK).
CREATE TABLE doctor_departments (
    staff_member_id uuid        PRIMARY KEY REFERENCES staff_members (id) ON DELETE CASCADE,
    department_id   uuid        NOT NULL REFERENCES departments (id),
    assigned_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_doctor_departments_department ON doctor_departments (department_id);

-- -----------------------------------------------------------------------------
-- 6. User accounts and sessions (US-01 login/logout, US-05 account management)
-- -----------------------------------------------------------------------------
CREATE TABLE user_accounts (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_member_id      uuid        NOT NULL UNIQUE REFERENCES staff_members (id) ON DELETE CASCADE,
    username             text        NOT NULL,
    password_hash        text        NOT NULL,      -- BCrypt / Argon2 hash, never plaintext
    status               text        NOT NULL DEFAULT 'ACTIVE',
    must_change_password boolean     NOT NULL DEFAULT false,   -- set by an administrator password reset
    password_changed_at  timestamptz NOT NULL DEFAULT now(),
    failed_login_count   integer     NOT NULL DEFAULT 0,
    status_changed_at    timestamptz NOT NULL DEFAULT now(),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    CONSTRAINT ck_user_accounts_password_hash CHECK (
        password_hash ~ '^(\{(bcrypt|argon2)\})?(\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}|\$argon2(id|i|d)\$v=[0-9]+\$.+)$'),
    CONSTRAINT ck_user_accounts_failed_login_count CHECK (failed_login_count >= 0)
);
CREATE UNIQUE INDEX uq_user_accounts_username ON user_accounts (lower(username));

CREATE TABLE auth_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_account_id uuid        NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash      bytea       NOT NULL,          -- SHA-256 of the issued token / JWT id, never the token
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,                   -- NULL = still active
    revoke_reason   text,                          -- NULL = still active
    client_ip       inet,                          -- NULL = not provided
    user_agent      text,                          -- NULL = not provided
    CONSTRAINT uq_auth_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_sessions_token_hash CHECK (octet_length(token_hash) = 32),
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_auth_sessions_revoke CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT ck_auth_sessions_revoke_reason CHECK (revoke_reason IN
        ('LOGOUT', 'EXPIRED', 'ACCOUNT_DEACTIVATED', 'ROLE_CHANGED', 'PASSWORD_CHANGED', 'ADMIN_REVOKED'))
);
CREATE INDEX idx_auth_sessions_active ON auth_sessions (user_account_id) WHERE revoked_at IS NULL;

-- -----------------------------------------------------------------------------
-- 7. Permission checks
-- -----------------------------------------------------------------------------
-- Administrators hold every permission ("can make any change", US-05).
CREATE FUNCTION has_permission(p_account_id uuid, p_permission text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM user_accounts a
        JOIN staff_members s ON s.id = a.staff_member_id
        JOIN roles r ON r.id = s.role_id
        WHERE a.id = p_account_id
          AND a.status = 'ACTIVE'
          AND (r.code = 'Administrator'
               OR EXISTS (SELECT 1
                          FROM role_permissions rp
                          JOIN permissions p ON p.id = rp.permission_id
                          WHERE rp.role_id = s.role_id AND p.code = p_permission)))
$$;

CREATE FUNCTION hms_require_actor() RETURNS uuid
    LANGUAGE plpgsql STABLE
AS $$
DECLARE
    v_actor uuid := hms_actor_id();
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HA401',
            MESSAGE = 'ACTOR_CONTEXT_MISSING',
            HINT    = 'Call SELECT hms_set_context(:accountId, :sessionId) at the start of the transaction.';
    END IF;
    RETURN v_actor;
END
$$;

CREATE FUNCTION hms_require_permission(p_permission text) RETURNS uuid
    LANGUAGE plpgsql STABLE
AS $$
DECLARE
    v_actor uuid := hms_require_actor();
BEGIN
    IF NOT has_permission(v_actor, p_permission) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HA403',
            MESSAGE = format('PERMISSION_DENIED: %s', p_permission),
            DETAIL  = p_permission;
    END IF;
    RETURN v_actor;
END
$$;

CREATE FUNCTION hms_actor_is_admin() RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM user_accounts a
        JOIN staff_members s ON s.id = a.staff_member_id
        JOIN roles r ON r.id = s.role_id
        WHERE a.id = hms_actor_id() AND a.status = 'ACTIVE' AND r.code = 'Administrator')
$$;

-- Hard deletes need a reason (max 1000 characters); missing -> HV001 for field deleteReason.
CREATE FUNCTION hms_require_delete_reason(p_table text) RETURNS text
    LANGUAGE plpgsql STABLE
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_reason text := hms_delete_reason();
    v_code   text;
BEGIN
    IF v_reason IS NULL THEN
        v_code := 'REQUIRED';
    ELSIF char_length(v_reason) > 1000 THEN
        v_code := 'TOO_LONG';
    ELSE
        RETURN v_reason;
    END IF;
    PERFORM hms_raise_validation(p_table, (
        SELECT jsonb_build_array(jsonb_build_object(
                   'field',     'deleteReason',
                   'column',    'delete_reason',
                   'dataType',  'text',
                   'error',     v_code,
                   'message',   format(e.message_tr, 'Silme nedeni', '1000'),
                   'messageEn', format(e.message_en, 'Deletion reason', '1000')))
        FROM validation_error_codes e
        WHERE e.code = v_code));
    RETURN NULL;
END
$$;

-- Generic write guard. Arguments: permission for INSERT, UPDATE, DELETE and,
-- optionally, 'REASON' when a delete needs a deletion reason.
CREATE FUNCTION hms_guard() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_permission text := CASE TG_OP WHEN 'INSERT' THEN TG_ARGV[0]
                                    WHEN 'UPDATE' THEN TG_ARGV[1]
                                    ELSE TG_ARGV[2] END;
BEGIN
    IF hms_trusted_op() = '' THEN
        PERFORM hms_require_permission(v_permission);
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF TG_NARGS > 3 AND TG_ARGV[3] = 'REASON' THEN
            PERFORM hms_require_delete_reason(TG_TABLE_NAME);
        END IF;
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

-- -----------------------------------------------------------------------------
-- 8. Table rules
-- -----------------------------------------------------------------------------
-- New permissions are granted to Administrator automatically.
CREATE FUNCTION permissions_grant_administrator() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_previous text := hms_trusted_op();
BEGIN
    PERFORM set_config('hms.trusted_op', 'system', true);
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, NEW.id FROM roles r WHERE r.code = 'Administrator'
    ON CONFLICT DO NOTHING;
    PERFORM set_config('hms.trusted_op', v_previous, true);
    RETURN NULL;
END
$$;

CREATE FUNCTION role_permissions_protect_administrator() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.role_id = (SELECT id FROM roles WHERE code = 'Administrator') THEN
        RAISE EXCEPTION USING ERRCODE = 'HS005', MESSAGE = 'ADMINISTRATOR_PERMISSIONS_LOCKED';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

-- Role change / delete rules: last administrator, own record, department assignment.
CREATE FUNCTION staff_members_rules() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_admin     smallint := (SELECT id FROM roles WHERE code = 'Administrator');
    v_physician smallint := (SELECT id FROM roles WHERE code = 'Physician');
    v_previous  text;
BEGIN
    IF TG_OP = 'INSERT' THEN
        RETURN NEW;
    END IF;

    -- Losing the Administrator role (role change or delete) must leave another active administrator.
    IF OLD.role_id = v_admin AND (TG_OP = 'DELETE' OR NEW.role_id IS DISTINCT FROM v_admin)
       AND EXISTS (SELECT 1 FROM user_accounts a WHERE a.staff_member_id = OLD.id AND a.status = 'ACTIVE') THEN
        PERFORM pg_advisory_xact_lock(7310001);
        IF NOT EXISTS (SELECT 1
                       FROM user_accounts a
                       JOIN staff_members s ON s.id = a.staff_member_id
                       WHERE a.status = 'ACTIVE' AND s.role_id = v_admin AND s.id <> OLD.id) THEN
            RAISE EXCEPTION USING ERRCODE = 'HS001', MESSAGE = 'LAST_ADMINISTRATOR';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        IF hms_trusted_op() = ''
           AND EXISTS (SELECT 1 FROM user_accounts a WHERE a.staff_member_id = OLD.id AND a.id = hms_actor_id()) THEN
            RAISE EXCEPTION USING ERRCODE = 'HS002', MESSAGE = 'SELF_DEACTIVATION: own staff record cannot be deleted';
        END IF;
        RETURN OLD;
    END IF;

    -- A physician who receives another role leaves the department automatically (logged as a delete).
    IF OLD.role_id = v_physician AND NEW.role_id IS DISTINCT FROM v_physician THEN
        v_previous := hms_trusted_op();
        PERFORM set_config('hms.trusted_op', 'system', true);
        DELETE FROM doctor_departments d WHERE d.staff_member_id = OLD.id;
        PERFORM set_config('hms.trusted_op', v_previous, true);
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION doctor_departments_rules() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM staff_members s
                   JOIN roles r ON r.id = s.role_id
                   WHERE s.id = NEW.staff_member_id AND r.code = 'Physician') THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HS004',
            MESSAGE = 'DOCTOR_ASSIGNMENT_CONFLICT',
            DETAIL  = 'Only staff members with the Physician role can be assigned to a department.';
    END IF;
    IF EXISTS (SELECT 1 FROM departments d WHERE d.id = NEW.department_id AND NOT d.is_active) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HS004',
            MESSAGE = 'DOCTOR_ASSIGNMENT_CONFLICT',
            DETAIL  = 'The department is inactive.';
    END IF;
    IF TG_OP = 'INSERT' OR NEW.department_id IS DISTINCT FROM OLD.department_id THEN
        NEW.assigned_at := now();
    ELSE
        NEW.assigned_at := OLD.assigned_at;
    END IF;
    RETURN NEW;
END
$$;

-- Account rules: permissions (self password change allowed), username and
-- staff link immutable, self-deactivation and last-administrator protection.
CREATE FUNCTION user_accounts_rules() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor  uuid     := hms_actor_id();
    v_op     text     := hms_trusted_op();
    v_admin  smallint := (SELECT id FROM roles WHERE code = 'Administrator');
    v_self   text[]   := ARRAY['password_hash', 'must_change_password', 'password_changed_at', 'updated_at', 'version'];
    v_auth   text[]   := ARRAY['failed_login_count', 'updated_at'];
BEGIN
    NEW.username := lower(btrim(NEW.username));

    IF TG_OP = 'INSERT' THEN
        IF v_op = '' THEN
            PERFORM hms_require_permission('STAFF_ACCOUNT_MANAGE');
        END IF;
        NEW.status_changed_at   := now();
        NEW.password_changed_at := now();
        RETURN NEW;
    END IF;

    IF v_op = 'auth' THEN
        IF (to_jsonb(NEW) - v_auth) IS DISTINCT FROM (to_jsonb(OLD) - v_auth) THEN
            RAISE EXCEPTION USING ERRCODE = 'HA403', MESSAGE = 'PERMISSION_DENIED: login bookkeeping may only change failed_login_count';
        END IF;
    ELSIF v_op = '' THEN
        IF v_actor IS NULL
           OR NOT (NEW.id = v_actor
                   AND (to_jsonb(NEW) - v_self) IS NOT DISTINCT FROM (to_jsonb(OLD) - v_self)
                   AND NOT NEW.must_change_password) THEN
            PERFORM hms_require_permission('STAFF_ACCOUNT_MANAGE');
        END IF;
        IF OLD.status = 'ACTIVE' AND NEW.status = 'DEACTIVATED' AND NEW.id = v_actor THEN
            RAISE EXCEPTION USING ERRCODE = 'HS002', MESSAGE = 'SELF_DEACTIVATION';
        END IF;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_changed_at := now();
    ELSE
        NEW.status_changed_at := OLD.status_changed_at;
    END IF;
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        NEW.password_changed_at := now();
    ELSE
        NEW.password_changed_at := OLD.password_changed_at;
    END IF;

    IF OLD.status = 'ACTIVE' AND NEW.status IS DISTINCT FROM 'ACTIVE'
       AND EXISTS (SELECT 1 FROM staff_members s WHERE s.id = OLD.staff_member_id AND s.role_id = v_admin) THEN
        PERFORM pg_advisory_xact_lock(7310001);
        IF NOT EXISTS (SELECT 1
                       FROM user_accounts a
                       JOIN staff_members s ON s.id = a.staff_member_id
                       WHERE a.status = 'ACTIVE' AND s.role_id = v_admin AND a.id <> OLD.id) THEN
            RAISE EXCEPTION USING ERRCODE = 'HS001', MESSAGE = 'LAST_ADMINISTRATOR';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

-- Account delete: administrators (STAFF_DELETE) with a reason; never the own
-- account, never the last active administrator.
CREATE FUNCTION user_accounts_before_delete() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_admin smallint := (SELECT id FROM roles WHERE code = 'Administrator');
BEGIN
    IF hms_trusted_op() = '' THEN
        PERFORM hms_require_permission('STAFF_DELETE');
        IF OLD.id = hms_actor_id() THEN
            RAISE EXCEPTION USING ERRCODE = 'HS002', MESSAGE = 'SELF_DEACTIVATION: own account cannot be deleted';
        END IF;
    END IF;
    PERFORM hms_require_delete_reason('user_accounts');
    IF OLD.status = 'ACTIVE'
       AND EXISTS (SELECT 1 FROM staff_members s WHERE s.id = OLD.staff_member_id AND s.role_id = v_admin) THEN
        PERFORM pg_advisory_xact_lock(7310001);
        IF NOT EXISTS (SELECT 1
                       FROM user_accounts a
                       JOIN staff_members s ON s.id = a.staff_member_id
                       WHERE a.status = 'ACTIVE' AND s.role_id = v_admin AND a.id <> OLD.id) THEN
            RAISE EXCEPTION USING ERRCODE = 'HS001', MESSAGE = 'LAST_ADMINISTRATOR';
        END IF;
    END IF;
    RETURN OLD;
END
$$;

-- Sessions can be removed by administrators (and with their account).
CREATE FUNCTION auth_sessions_before_delete() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
BEGIN
    IF hms_trusted_op() = '' THEN
        PERFORM hms_require_permission('STAFF_ACCOUNT_MANAGE');
    END IF;
    RETURN OLD;
END
$$;

-- Deactivation, role change and password change end existing sessions.
CREATE FUNCTION revoke_account_sessions(p_account_id uuid, p_reason text, p_keep_session uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_count integer;
BEGIN
    UPDATE auth_sessions s
       SET revoked_at = now(), revoke_reason = p_reason
     WHERE s.user_account_id = p_account_id
       AND s.revoked_at IS NULL
       AND s.id IS DISTINCT FROM p_keep_session;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END
$$;

CREATE FUNCTION user_accounts_after_update() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
BEGIN
    IF OLD.status = 'ACTIVE' AND NEW.status = 'DEACTIVATED' THEN
        PERFORM revoke_account_sessions(NEW.id, 'ACCOUNT_DEACTIVATED', NULL);
    ELSIF NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        -- A user changing their own password keeps the current session.
        PERFORM revoke_account_sessions(NEW.id, 'PASSWORD_CHANGED',
            CASE WHEN NEW.id = hms_actor_id() AND NOT NEW.must_change_password THEN hms_session_id() END);
    END IF;
    RETURN NULL;
END
$$;

CREATE FUNCTION staff_members_after_update() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.role_id IS DISTINCT FROM OLD.role_id THEN
        PERFORM revoke_account_sessions(a.id, 'ROLE_CHANGED', NULL)
        FROM user_accounts a WHERE a.staff_member_id = NEW.id;
    END IF;
    RETURN NULL;
END
$$;

-- Sessions are created only through auth_login_success() and can only be revoked once.
CREATE FUNCTION auth_sessions_rules() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF hms_trusted_op() <> 'auth' THEN
            RAISE EXCEPTION USING ERRCODE = 'HA403', MESSAGE = 'PERMISSION_DENIED: sessions are created by auth_login_success()';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM user_accounts a WHERE a.id = NEW.user_account_id AND a.status = 'ACTIVE') THEN
            RAISE EXCEPTION USING ERRCODE = 'HS003', MESSAGE = 'ACCOUNT_INACTIVE';
        END IF;
        NEW.issued_at     := now();
        NEW.revoked_at    := NULL;
        NEW.revoke_reason := NULL;
        RETURN NEW;
    END IF;

    IF OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL
       OR (to_jsonb(NEW) - ARRAY['revoked_at', 'revoke_reason'])
          IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['revoked_at', 'revoke_reason']) THEN
        RAISE EXCEPTION USING ERRCODE = 'HS007', MESSAGE = 'SESSION_STATE_LOCKED';
    END IF;
    NEW.revoked_at := now();
    RETURN NEW;
END
$$;

-- Trigger order on each table: 10 guard -> 20 rules -> 30 validate -> 40 touch.
CREATE TRIGGER trg_10_role_permissions_guard BEFORE INSERT OR UPDATE OR DELETE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION hms_guard('ROLE_PERMISSION_MANAGE', 'ROLE_PERMISSION_MANAGE', 'ROLE_PERMISSION_MANAGE');
CREATE TRIGGER trg_20_role_permissions_admin BEFORE UPDATE OR DELETE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION role_permissions_protect_administrator();
CREATE TRIGGER trg_50_permissions_grant_admin AFTER INSERT ON permissions
    FOR EACH ROW EXECUTE FUNCTION permissions_grant_administrator();

CREATE TRIGGER trg_10_departments_guard BEFORE INSERT OR UPDATE OR DELETE ON departments
    FOR EACH ROW EXECUTE FUNCTION hms_guard('DEPARTMENT_MANAGE', 'DEPARTMENT_MANAGE', 'DEPARTMENT_MANAGE');
CREATE TRIGGER trg_30_departments_validate BEFORE INSERT OR UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION hms_validate_row();
CREATE TRIGGER trg_40_departments_touch BEFORE INSERT OR UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION hms_touch();

CREATE TRIGGER trg_10_staff_members_guard BEFORE INSERT OR UPDATE OR DELETE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION hms_guard('STAFF_ACCOUNT_MANAGE', 'STAFF_ACCOUNT_MANAGE', 'STAFF_DELETE', 'REASON');
CREATE TRIGGER trg_20_staff_members_rules BEFORE INSERT OR UPDATE OR DELETE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION staff_members_rules();
CREATE TRIGGER trg_30_staff_members_validate BEFORE INSERT OR UPDATE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION hms_validate_row();
CREATE TRIGGER trg_40_staff_members_touch BEFORE INSERT OR UPDATE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION hms_touch();
CREATE TRIGGER trg_50_staff_members_sessions AFTER UPDATE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION staff_members_after_update();

CREATE TRIGGER trg_10_doctor_departments_guard BEFORE INSERT OR UPDATE OR DELETE ON doctor_departments
    FOR EACH ROW EXECUTE FUNCTION hms_guard('DEPARTMENT_MANAGE', 'DEPARTMENT_MANAGE', 'DEPARTMENT_MANAGE');
CREATE TRIGGER trg_20_doctor_departments_rules BEFORE INSERT OR UPDATE ON doctor_departments
    FOR EACH ROW EXECUTE FUNCTION doctor_departments_rules();

CREATE TRIGGER trg_20_user_accounts_rules BEFORE INSERT OR UPDATE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION user_accounts_rules();
CREATE TRIGGER trg_30_user_accounts_validate BEFORE INSERT OR UPDATE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION hms_validate_row();
CREATE TRIGGER trg_40_user_accounts_touch BEFORE INSERT OR UPDATE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION hms_touch();
CREATE TRIGGER trg_15_user_accounts_delete BEFORE DELETE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION user_accounts_before_delete();
CREATE TRIGGER trg_50_user_accounts_sessions AFTER UPDATE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION user_accounts_after_update();

CREATE TRIGGER trg_20_auth_sessions_rules BEFORE INSERT OR UPDATE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION auth_sessions_rules();
CREATE TRIGGER trg_15_auth_sessions_delete BEFORE DELETE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION auth_sessions_before_delete();

-- -----------------------------------------------------------------------------
-- 9. Validation rules for staff tables
-- -----------------------------------------------------------------------------
INSERT INTO field_rules (table_name, column_name, api_field, data_type, label_tr, label_en, required,
                         max_length, pattern, allowed_values, unique_mode, immutable, sort_order) VALUES
('departments',   'name',            'name',          'text',     'Departman adı',  'Department name', true,  100,  NULL, NULL, 'ci', false, 1),
('staff_members', 'role_id',         'roleId',        'smallint', 'Rol',            'Role',            true,  NULL, NULL, NULL, NULL, false, 1),
('staff_members', 'first_name',      'firstName',     'text',     'Ad',             'First name',      true,  100,  NULL, NULL, NULL, false, 2),
('staff_members', 'last_name',       'lastName',      'text',     'Soyad',          'Last name',       true,  100,  NULL, NULL, NULL, false, 3),
('staff_members', 'title',           'title',         'text',     'Ünvan',          'Title',           false, 50,   NULL, NULL, NULL, false, 4),
('staff_members', 'phone',           'phone',         'text',     'Telefon',        'Phone',           false, 32,   '^\+?[0-9 ()-]{7,31}$', NULL, NULL, false, 5),
('staff_members', 'email',           'email',         'text',     'E-posta',        'Email',           false, 254,  '^[^@\s]+@[^@\s]+\.[^@\s]+$', NULL, NULL, false, 6),
('user_accounts', 'staff_member_id', 'staffMemberId', 'uuid',     'Personel',       'Staff member',    true,  NULL, NULL, NULL, NULL, true,  1),
('user_accounts', 'username',        'username',      'text',     'Kullanıcı adı',  'Username',        true,  50,   '^[a-z0-9._-]{3,50}$', NULL, 'ci', true, 2),
('user_accounts', 'password_hash',   'password',      'text',     'Parola',         'Password',        true,  NULL,
 '^(\{(bcrypt|argon2)\})?(\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}|\$argon2(id|i|d)\$v=[0-9]+\$.+)$', NULL, NULL, false, 3),
('user_accounts', 'status',          'status',        'text',     'Hesap durumu',   'Account status',  true,  NULL, NULL, '{ACTIVE,DEACTIVATED}', NULL, false, 4);

-- -----------------------------------------------------------------------------
-- 10. Permission catalogue and initial matrix (Administrator gets every
--     permission automatically; other roles receive theirs in later stories)
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, module, story, description_tr, description_en) VALUES
(1, 'STAFF_ACCOUNT_MANAGE',   'security', 'US-05',
 'Personel hesabı oluşturma, düzenleme, devre dışı bırakma, rol değiştirme ve parola sıfırlama',
 'Create, edit and deactivate staff accounts, change roles, reset passwords'),
(2, 'ROLE_PERMISSION_MANAGE', 'security', 'US-05',
 'Rol–yetki matrisini düzenleme', 'Edit the role-permission matrix'),
(3, 'DEPARTMENT_MANAGE',      'staff',    'US-05',
 'Departman ekleme/düzenleme ve hekimleri departmanlara atama', 'Add/edit departments and assign doctors'),
(8, 'STAFF_DELETE',           'security', 'US-05',
 'Personel kaydını ve hesabını silme nedeniyle kalıcı olarak silme', 'Permanently delete staff records and accounts, with a reason');

-- -----------------------------------------------------------------------------
-- 11. Read model for the clinician directory (US-09 / Clinician schema)
-- -----------------------------------------------------------------------------
CREATE VIEW doctors AS
SELECT s.id,
       s.display_name,
       s.first_name,
       s.last_name,
       s.title,
       d.department_id,
       dep.name AS department
FROM staff_members s
JOIN roles r              ON r.id = s.role_id AND r.code = 'Physician'
JOIN doctor_departments d ON d.staff_member_id = s.id
JOIN departments dep      ON dep.id = d.department_id AND dep.is_active
WHERE NOT EXISTS (SELECT 1 FROM user_accounts a
                  WHERE a.staff_member_id = s.id AND a.status = 'DEACTIVATED');

-- -----------------------------------------------------------------------------
-- 12. First administrator (deployment step; works only while no administrator exists)
-- -----------------------------------------------------------------------------
CREATE FUNCTION security_bootstrap_admin(p_first_name text, p_last_name text,
                                         p_username text, p_password_hash text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_staff   uuid;
    v_account uuid;
BEGIN
    PERFORM pg_advisory_xact_lock(7310001);
    IF EXISTS (SELECT 1 FROM staff_members s JOIN roles r ON r.id = s.role_id WHERE r.code = 'Administrator') THEN
        RAISE EXCEPTION USING ERRCODE = 'HS008', MESSAGE = 'BOOTSTRAP_NOT_ALLOWED';
    END IF;
    PERFORM set_config('hms.trusted_op', 'bootstrap', true);
    INSERT INTO staff_members (role_id, first_name, last_name)
    SELECT r.id, p_first_name, p_last_name FROM roles r WHERE r.code = 'Administrator'
    RETURNING id INTO v_staff;
    INSERT INTO user_accounts (staff_member_id, username, password_hash, must_change_password)
    VALUES (v_staff, p_username, p_password_hash, true)
    RETURNING id INTO v_account;
    PERFORM set_config('hms.trusted_op', '', true);
    RETURN v_account;
END
$$;
REVOKE EXECUTE ON FUNCTION security_bootstrap_admin(text, text, text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION revoke_account_sessions(uuid, text, uuid) FROM PUBLIC;

-- -----------------------------------------------------------------------------
-- 13. Hard deletes by administrators (reason is mandatory and logged)
-- -----------------------------------------------------------------------------
-- Where the deletion reason lives: the administrator types it as free text
-- (API: DELETE request body {"reason": "..."}), the backend passes it to
-- patient_delete / staff_delete / user_account_delete (p_reason), and it is
-- stored here (reason) and in the audit event. Rows cannot be changed or removed.
CREATE TABLE record_deletions (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type text        NOT NULL CHECK (entity_type IN ('PATIENT', 'STAFF_MEMBER', 'USER_ACCOUNT')),
    entity_id   uuid        NOT NULL,
    reason      text        NOT NULL CHECK (btrim(reason) <> '' AND char_length(reason) <= 1000),
    deleted_by  uuid,                                   -- administrator account; NULL = system operation
    deleted_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_record_deletions_entity ON record_deletions (entity_type, entity_id);
CREATE INDEX idx_record_deletions_time   ON record_deletions (deleted_at);

CREATE FUNCTION record_deletions_immutable() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = 'HL001',
        MESSAGE = format('AUDIT_IMMUTABLE: %s on record_deletions is not allowed', TG_OP);
END
$$;
CREATE TRIGGER trg_record_deletions_immutable BEFORE UPDATE OR DELETE ON record_deletions
    FOR EACH STATEMENT EXECUTE FUNCTION record_deletions_immutable();
CREATE TRIGGER trg_record_deletions_truncate BEFORE TRUNCATE ON record_deletions
    FOR EACH STATEMENT EXECUTE FUNCTION record_deletions_immutable();

-- AFTER DELETE: stores entity, reason and the deleting administrator.
CREATE FUNCTION hms_record_deletion() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
BEGIN
    INSERT INTO record_deletions (entity_type, entity_id, reason, deleted_by)
    VALUES (TG_ARGV[0], (to_jsonb(OLD) ->> 'id')::uuid, hms_delete_reason(), hms_actor_id());
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_95_staff_members_deletion AFTER DELETE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION hms_record_deletion('STAFF_MEMBER');
CREATE TRIGGER trg_95_user_accounts_deletion AFTER DELETE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION hms_record_deletion('USER_ACCOUNT');
-- Deletes the staff member together with the account, sessions and department assignment.
CREATE FUNCTION staff_delete(p_staff_member_id uuid, p_reason text) RETURNS boolean
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_count integer;
BEGIN
    PERFORM hms_set_delete_reason(p_reason);
    DELETE FROM staff_members WHERE id = p_staff_member_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    PERFORM hms_set_delete_reason(NULL);
    RETURN v_count > 0;
END
$$;

-- Deletes only the login (the staff member stays, e.g. in the doctor directory).
CREATE FUNCTION user_account_delete(p_account_id uuid, p_reason text) RETURNS boolean
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_count integer;
BEGIN
    PERFORM hms_set_delete_reason(p_reason);
    DELETE FROM user_accounts WHERE id = p_account_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    PERFORM hms_set_delete_reason(NULL);
    RETURN v_count > 0;
END
$$;

-- -----------------------------------------------------------------------------
-- 14. Grants for the backend runtime role (row rules above decide who may delete)
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hms_app') THEN
        GRANT SELECT ON hms_error_codes, validation_error_codes, field_rules, roles, permissions,
                        role_permissions, departments, staff_members, doctor_departments,
                        user_accounts, auth_sessions, doctors, record_deletions TO hms_app;
        GRANT INSERT, DELETE ON role_permissions TO hms_app;
        GRANT INSERT, UPDATE, DELETE ON departments, doctor_departments, staff_members, user_accounts TO hms_app;
        GRANT DELETE ON auth_sessions TO hms_app;
    END IF;
END
$$;

SELECT set_config('hms.trusted_op', '', false);
