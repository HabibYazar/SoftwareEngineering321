-- =============================================================================
-- HMS Community | Flyway migration V2
-- Immutable audit log of every action (who, role, what, which record, when,
-- before/after values), login/logout lifecycle, administrator-only log search,
-- monthly partitions and compact archiving.
--
-- Stories: US-03 Logging of all actions (#140 data, #145 backend)
--          US-04 View audit log (#152) - administrator-only read access
--          US-01 Staff login (#107) - login, failed login, logout events
--
-- Space efficiency
--   * Codes are stored as smallint (action, role, entity type, result) and
--     resolved through small lookup tables; IP / user agent live once per
--     session, not per event; details are optional jsonb (NULL when empty).
--   * Monthly range partitions keep indexes small; toast_tuple_target = 128
--     lets PostgreSQL compress larger rows in place.
--   * Closed months are packed with scripts/audit_archive.py into one zip
--     file with one xz block per column (columnar, compressed in parallel),
--     verified with SHA-256, and can be opened as CSV or restored into the
--     database at any time.
--
-- Immutability
--   * UPDATE / DELETE / TRUNCATE on audit tables raise SQLSTATE HL001 for
--     every role, including the owner. Rows can only be inserted through
--     audit_record() / audit_write() which set the timestamp themselves.
--   * The backend role has no table privileges on audit_log; administrators
--     read it only through audit_search(), which checks AUDIT_LOG_VIEW and
--     records the read itself.
--   * When migrations run as a superuser, event triggers also block DROP of
--     audit tables and disabling/removing their triggers. Only a partition
--     that has been archived and verified can be dropped, through
--     audit_archive_drop_partition().
-- =============================================================================

SELECT set_config('hms.trusted_op', 'migration', false);

INSERT INTO hms_error_codes (sqlstate, name, http_status, message_tr, message_en, story) VALUES
('HL001', 'AUDIT_IMMUTABLE', 403,
 'Log kayıtları değiştirilemez ve silinemez.', 'Audit log entries cannot be changed or deleted.', 'US-03'),
('HL002', 'AUDIT_UNKNOWN_CODE', 400,
 'Bilinmeyen log eylemi, kayıt türü veya sonuç kodu.', 'Unknown audit action, entity type or result code.', 'US-03'),
('HL003', 'AUDIT_DETAILS_TOO_LARGE', 400,
 'Log ayrıntısı 8 KB sınırını aşıyor.', 'Audit details exceed the 8 KB limit.', 'US-03'),
('HL004', 'AUDIT_ARCHIVE_STATE', 409,
 'Arşivleme işlemi bu dönem için şu anda yapılamaz.', 'The archive operation is not possible for this period now.', 'US-03');

-- -----------------------------------------------------------------------------
-- 1. Lookup tables (small, stable codes)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_actions (
    id             smallint PRIMARY KEY,
    code           text     NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    category       text     NOT NULL CHECK (category IN ('AUTH', 'UI', 'DATA', 'ADMIN', 'SECURITY', 'AUDIT', 'SYSTEM')),
    description_tr text     NOT NULL,
    description_en text     NOT NULL
);

INSERT INTO audit_actions (id, code, category, description_tr, description_en) VALUES
( 1, 'LOGIN_SUCCESS',            'AUTH',     'Başarılı giriş',                                  'Successful sign-in'),
( 2, 'LOGIN_FAILURE',            'AUTH',     'Başarısız giriş denemesi',                        'Failed sign-in attempt'),
( 3, 'LOGOUT',                   'AUTH',     'Çıkış',                                           'Sign-out'),
( 4, 'ACCOUNT_PASSWORD_CHANGED', 'AUTH',     'Kullanıcının kendi parolasını değiştirmesi',      'User changed own password'),
(10, 'UI_CLICK',                 'UI',       'Arayüzde tıklama',                                'UI click'),
(11, 'UI_NAVIGATION',            'UI',       'Sayfa / ekran geçişi',                            'Page or screen navigation'),
(12, 'UI_INPUT',                 'UI',       'Form alanı etkileşimi (girilen değer kaydedilmez)', 'Form field interaction (value is not stored)'),
(13, 'UI_FORM_SUBMIT',           'UI',       'Form gönderimi',                                  'Form submission'),
(14, 'UI_OTHER',                 'UI',       'Diğer arayüz etkileşimi',                         'Other UI interaction'),
(20, 'RECORD_VIEW',              'DATA',     'Kayıt görüntüleme',                               'Record viewed'),
(21, 'RECORD_CREATE',            'DATA',     'Kayıt oluşturma',                                 'Record created'),
(22, 'RECORD_UPDATE',            'DATA',     'Kayıt güncelleme (önceki/sonraki değerler)',      'Record updated (before/after values)'),
(23, 'RECORD_DELETE',            'DATA',     'Kayıt silme',                                     'Record deleted'),
(24, 'RECORD_SEARCH',            'DATA',     'Kayıt arama',                                     'Record search'),
(30, 'ACCOUNT_CREATED',          'ADMIN',    'Personel hesabı oluşturma',                       'Staff account created'),
(31, 'ACCOUNT_DEACTIVATED',      'ADMIN',    'Hesabı devre dışı bırakma',                       'Account deactivated'),
(32, 'ACCOUNT_REACTIVATED',      'ADMIN',    'Hesabı yeniden etkinleştirme',                    'Account reactivated'),
(33, 'ACCOUNT_ROLE_CHANGED',     'ADMIN',    'Personel rolünü değiştirme',                      'Staff role changed'),
(34, 'ACCOUNT_PASSWORD_RESET',   'ADMIN',    'Yönetici tarafından parola sıfırlama',            'Password reset by an administrator'),
(35, 'ROLE_PERMISSION_GRANTED',  'ADMIN',    'Role yetki verme',                                'Permission granted to a role'),
(36, 'ROLE_PERMISSION_REVOKED',  'ADMIN',    'Rolden yetki kaldırma',                           'Permission revoked from a role'),
(37, 'ACCOUNT_DELETED',          'ADMIN',    'Hesabın silme nedeniyle kalıcı olarak silinmesi', 'Account permanently deleted (with reason)'),
(38, 'SESSION_REVOKED',          'ADMIN',    'Yönetici tarafından oturum sonlandırma',          'Session ended by an administrator'),
(40, 'PERMISSION_DENIED',        'SECURITY', 'Yetkisiz erişim denemesi (HTTP 403)',             'Access denied (HTTP 403)'),
(41, 'API_REQUEST',              'SECURITY', 'API isteği',                                      'API request'),
(50, 'AUDIT_LOG_VIEWED',         'AUDIT',    'Log kayıtlarını görüntüleme',                     'Audit log viewed'),
(51, 'AUDIT_ARCHIVE_EXPORTED',   'AUDIT',    'Aylık logun arşiv dosyasına paketlenmesi',        'Monthly log packed into an archive file'),
(52, 'AUDIT_PARTITION_DROPPED',  'AUDIT',    'Arşivlenmiş ayın veritabanından kaldırılması',    'Archived month removed from the live database'),
(53, 'AUDIT_ARCHIVE_RESTORED',   'AUDIT',    'Arşivin veritabanına geri yüklenmesi',            'Archive restored into the database'),
(54, 'AUDIT_RESTORE_UNLOADED',   'AUDIT',    'Geri yüklenen arşiv kopyasının kaldırılması',     'Restored archive copy unloaded'),
(60, 'SYSTEM_EVENT',             'SYSTEM',   'Sistem olayı',                                    'System event');

CREATE TABLE audit_entity_types (
    id             smallint PRIMARY KEY,
    code           text     NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    table_name     text     NOT NULL,
    description_tr text     NOT NULL
);

INSERT INTO audit_entity_types (id, code, table_name, description_tr) VALUES
(1, 'PATIENT',           'patients',           'Hasta'),
(2, 'PATIENT_RELATIVE',  'patient_relatives',  'Hasta yakını'),
(3, 'STAFF_MEMBER',      'staff_members',      'Personel'),
(4, 'USER_ACCOUNT',      'user_accounts',      'Kullanıcı hesabı'),
(5, 'AUTH_SESSION',      'auth_sessions',      'Oturum'),
(6, 'ROLE_PERMISSION',   'role_permissions',   'Rol yetkisi'),
(7, 'DEPARTMENT',        'departments',        'Departman'),
(8, 'DOCTOR_DEPARTMENT', 'doctor_departments', 'Hekim–departman ataması'),
(9, 'AUDIT_LOG',         'audit_log',          'Log kaydı');

-- -----------------------------------------------------------------------------
-- 2. Audit log (partitioned by month, UTC)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE audit_log_id_seq AS bigint;

-- Column order keeps 8/16-byte values first and 2-byte codes together (no padding).
-- NULL in actor_user_id = system action / unknown user (e.g. failed sign-in with an unknown username).
CREATE TABLE audit_log (
    id             bigint      NOT NULL DEFAULT nextval('audit_log_id_seq'),
    occurred_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_user_id  uuid,
    session_id     uuid,
    entity_id      uuid,
    actor_role_id  smallint,                          -- role of the actor at the time of the event
    action_id      smallint    NOT NULL REFERENCES audit_actions (id),
    result         smallint    NOT NULL,              -- 1 SUCCESS, 2 FAILURE, 3 DENIED
    entity_type_id smallint    REFERENCES audit_entity_types (id),
    details        jsonb,
    CONSTRAINT pk_audit_log PRIMARY KEY (id, occurred_at),
    CONSTRAINT ck_audit_log_result CHECK (result IN (1, 2, 3))
) PARTITION BY RANGE (occurred_at);

ALTER SEQUENCE audit_log_id_seq OWNED BY audit_log.id;

CREATE INDEX idx_audit_log_actor  ON audit_log (actor_user_id, occurred_at);
CREATE INDEX idx_audit_log_action ON audit_log (action_id, occurred_at);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type_id, entity_id) WHERE entity_id IS NOT NULL;
CREATE INDEX brin_audit_log_occurred_at ON audit_log USING brin (occurred_at);

CREATE FUNCTION audit_block_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = 'HL001',
        MESSAGE = format('AUDIT_IMMUTABLE: %s on %s is not allowed', TG_OP, TG_TABLE_NAME),
        TABLE   = TG_TABLE_NAME;
END
$$;

CREATE FUNCTION audit_log_insert_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('hms.audit_insert', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HL001',
            MESSAGE = 'AUDIT_IMMUTABLE: rows are written only by audit_record() / audit_write()';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_audit_log_insert BEFORE INSERT ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_insert_guard();
CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_block_change();
CREATE TRIGGER trg_audit_log_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_block_change();
-- Statement level as well: an UPDATE/DELETE fails even when it matches no row.
CREATE TRIGGER trg_audit_log_statement BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_block_change();

-- -----------------------------------------------------------------------------
-- 3. Archive registry and restored copies
-- -----------------------------------------------------------------------------
CREATE TABLE audit_archives (
    period_start      date        PRIMARY KEY CHECK (extract(day FROM period_start) = 1),
    partition_name    text        NOT NULL,
    row_count         bigint      NOT NULL CHECK (row_count >= 0),
    min_id            bigint,                        -- NULL when the month had no rows
    max_id            bigint,
    file_name         text        NOT NULL,
    file_sha256       text        NOT NULL CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    content_sha256    text        NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    file_bytes        bigint      NOT NULL CHECK (file_bytes > 0),
    content_bytes     bigint      NOT NULL CHECK (content_bytes >= 0),   -- 0 for a month without events
    compression       text        NOT NULL,
    status            text        NOT NULL DEFAULT 'ARCHIVED' CHECK (status IN ('ARCHIVED', 'PARTITION_DROPPED')),
    archived_at       timestamptz NOT NULL DEFAULT now(),
    archived_by       text        NOT NULL DEFAULT current_user,   -- database role of the operator
    status_changed_at timestamptz NOT NULL DEFAULT now()
);

-- Readable copies of archived months, loaded on demand (audit_restore_begin /
-- COPY / audit_restore_finish). The archive file is the authoritative copy, so
-- this table is UNLOGGED (no WAL: restoring is ~2x faster; after a crash it is
-- empty and the month can simply be restored again). Rows are only written by
-- the restore functions, which verify count and id range against audit_archives.
CREATE UNLOGGED TABLE audit_log_restored (
    id              bigint      PRIMARY KEY,
    occurred_at     timestamptz NOT NULL,
    actor_user_id   uuid,
    actor_username  text,
    actor_role      text,
    action          text        NOT NULL,
    action_category text        NOT NULL,
    result          text        NOT NULL,
    entity_type     text,
    entity_id       uuid,
    session_id      uuid,
    details         jsonb,
    -- set by audit_restore_begin(); outside a restore the setting is missing and inserts fail
    period_start    date        NOT NULL DEFAULT current_setting('hms.restore_period')::date
);
CREATE INDEX idx_audit_log_restored_period ON audit_log_restored (period_start);

CREATE FUNCTION audit_archives_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' AND hms_trusted_op() = 'archive' THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' AND hms_trusted_op() = 'archive'
       AND OLD.status = 'ARCHIVED' AND NEW.status = 'PARTITION_DROPPED'
       AND (to_jsonb(NEW) - ARRAY['status', 'status_changed_at'])
           = (to_jsonb(OLD) - ARRAY['status', 'status_changed_at']) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = 'HL001',
        MESSAGE = format('AUDIT_IMMUTABLE: %s on audit_archives is not allowed', TG_OP);
END
$$;

-- Statement level (one check per statement, so restoring a month stays fast):
-- UPDATE/DELETE fail even when they match no row. Allowed only inside the
-- archive functions: status change of audit_archives, load/unload of restored copies.
CREATE FUNCTION audit_statement_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF hms_trusted_op() = 'archive'
       AND ((TG_TABLE_NAME = 'audit_archives' AND TG_OP = 'UPDATE')
            OR (TG_TABLE_NAME = 'audit_log_restored' AND TG_OP IN ('INSERT', 'DELETE'))) THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = 'HL001',
        MESSAGE = format('AUDIT_IMMUTABLE: %s on %s is not allowed', TG_OP, TG_TABLE_NAME),
        TABLE   = TG_TABLE_NAME;
END
$$;

CREATE TRIGGER trg_audit_archives_guard BEFORE INSERT OR UPDATE OR DELETE ON audit_archives
    FOR EACH ROW EXECUTE FUNCTION audit_archives_guard();
CREATE TRIGGER trg_audit_archives_statement BEFORE UPDATE OR DELETE ON audit_archives
    FOR EACH STATEMENT EXECUTE FUNCTION audit_statement_guard();
CREATE TRIGGER trg_audit_restored_statement BEFORE INSERT OR UPDATE OR DELETE ON audit_log_restored
    FOR EACH STATEMENT EXECUTE FUNCTION audit_statement_guard();
CREATE TRIGGER trg_audit_archives_truncate BEFORE TRUNCATE ON audit_archives
    FOR EACH STATEMENT EXECUTE FUNCTION audit_block_change();
CREATE TRIGGER trg_audit_restored_truncate BEFORE TRUNCATE ON audit_log_restored
    FOR EACH STATEMENT EXECUTE FUNCTION audit_block_change();

-- -----------------------------------------------------------------------------
-- 4. Partition management
-- -----------------------------------------------------------------------------
CREATE FUNCTION audit_partition_name(p_month date) RETURNS text
    LANGUAGE sql IMMUTABLE
AS $$ SELECT 'audit_log_y' || to_char(p_month, 'YYYY') || 'm' || to_char(p_month, 'MM') $$;

CREATE FUNCTION audit_ensure_partition(p_at timestamptz) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_month date := date_trunc('month', p_at AT TIME ZONE 'UTC')::date;
    v_name  text := audit_partition_name(v_month);
BEGIN
    IF to_regclass('public.' || v_name) IS NOT NULL THEN
        RETURN v_name;
    END IF;
    PERFORM pg_advisory_xact_lock(7310002);
    IF to_regclass('public.' || v_name) IS NOT NULL THEN
        RETURN v_name;
    END IF;
    IF EXISTS (SELECT 1 FROM audit_archives a WHERE a.period_start = v_month) THEN
        RAISE EXCEPTION USING
            ERRCODE = 'HL004',
            MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is already archived', v_name);
    END IF;
    EXECUTE format(
        'CREATE TABLE public.%I PARTITION OF public.audit_log FOR VALUES FROM (%L) TO (%L) WITH (toast_tuple_target = 128)',
        v_name,
        v_month::timestamp AT TIME ZONE 'UTC',
        (v_month + interval '1 month')::timestamp AT TIME ZONE 'UTC');
    EXECUTE format(
        'CREATE TRIGGER trg_audit_log_truncate BEFORE TRUNCATE ON public.%I FOR EACH STATEMENT EXECUTE FUNCTION audit_block_change()',
        v_name);
    RETURN v_name;
END
$$;

-- Creates the current and the next p_months_ahead monthly partitions.
-- Schedule it (e.g. daily from the backend) so partitions never have to be
-- created inside a busy business transaction.
CREATE FUNCTION audit_maintain_partitions(p_months_ahead integer DEFAULT 3) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_created integer := 0;
    v_name    text;
    i         integer;
BEGIN
    FOR i IN 0 .. greatest(p_months_ahead, 0) LOOP
        v_name := audit_partition_name((date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i))::date);
        IF to_regclass('public.' || v_name) IS NULL THEN
            PERFORM audit_ensure_partition((date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i)) AT TIME ZONE 'UTC');
            v_created := v_created + 1;
        END IF;
    END LOOP;
    RETURN v_created;
END
$$;

-- -----------------------------------------------------------------------------
-- 5. Writing events
-- -----------------------------------------------------------------------------
-- Privacy filter: secrets never reach the log, even if a caller sends them.
CREATE FUNCTION audit_redact(p jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE
AS $$
DECLARE
    v_out jsonb;
BEGIN
    IF jsonb_typeof(p) = 'object' THEN
        SELECT coalesce(jsonb_object_agg(e.key,
                   CASE WHEN e.key ~* '(password|passwd|parola|sifre|şifre|token|secret|hash)'
                        THEN to_jsonb('[REDACTED]'::text)
                        ELSE audit_redact(e.value) END), '{}'::jsonb)
          INTO v_out
          FROM jsonb_each(p) e;
        RETURN v_out;
    ELSIF jsonb_typeof(p) = 'array' THEN
        SELECT coalesce(jsonb_agg(audit_redact(x.value) ORDER BY x.ord), '[]'::jsonb)
          INTO v_out
          FROM jsonb_array_elements(p) WITH ORDINALITY AS x(value, ord);
        RETURN v_out;
    END IF;
    RETURN p;
END
$$;

-- Internal writer (not granted to the backend role).
CREATE FUNCTION audit_write(p_actor uuid, p_action text, p_entity_type text, p_entity_id uuid,
                            p_details jsonb, p_result text, p_session uuid) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_action  smallint;
    v_entity  smallint;
    v_role    smallint;
    v_result  smallint;
    v_details jsonb;
    v_now     timestamptz := clock_timestamp();
    v_id      bigint;
BEGIN
    SELECT a.id INTO v_action FROM audit_actions a WHERE a.code = p_action;
    IF v_action IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL002', MESSAGE = format('AUDIT_UNKNOWN_CODE: action %s', coalesce(p_action, '<null>'));
    END IF;
    IF p_entity_type IS NOT NULL THEN
        SELECT e.id INTO v_entity FROM audit_entity_types e WHERE e.code = p_entity_type;
        IF v_entity IS NULL THEN
            RAISE EXCEPTION USING ERRCODE = 'HL002', MESSAGE = format('AUDIT_UNKNOWN_CODE: entity type %s', p_entity_type);
        END IF;
    END IF;
    v_result := CASE upper(coalesce(p_result, 'SUCCESS'))
                    WHEN 'SUCCESS' THEN 1 WHEN 'FAILURE' THEN 2 WHEN 'DENIED' THEN 3 END;
    IF v_result IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL002', MESSAGE = format('AUDIT_UNKNOWN_CODE: result %s', p_result);
    END IF;
    IF p_actor IS NOT NULL THEN
        SELECT s.role_id INTO v_role
          FROM user_accounts a JOIN staff_members s ON s.id = a.staff_member_id
         WHERE a.id = p_actor;
    END IF;
    IF p_details IS NOT NULL AND jsonb_typeof(p_details) <> 'null' AND p_details <> '{}'::jsonb THEN
        v_details := audit_redact(p_details);
        IF octet_length(v_details::text) > 8192 THEN
            RAISE EXCEPTION USING ERRCODE = 'HL003', MESSAGE = 'AUDIT_DETAILS_TOO_LARGE';
        END IF;
    END IF;

    PERFORM audit_ensure_partition(v_now);
    PERFORM set_config('hms.audit_insert', 'on', true);
    INSERT INTO audit_log (occurred_at, actor_user_id, session_id, entity_id, actor_role_id,
                           action_id, result, entity_type_id, details)
    VALUES (v_now, p_actor, p_session, p_entity_id, v_role, v_action, v_result, v_entity, v_details)
    RETURNING id INTO v_id;
    PERFORM set_config('hms.audit_insert', '', true);
    RETURN v_id;
END
$$;

-- Backend API: records one event for the actor/session of the current transaction.
-- Example (UI click forwarded by the frontend):
--   SELECT audit_record('UI_CLICK', NULL, NULL, '{"element":"save","route":"/patients/new"}');
CREATE FUNCTION audit_record(p_action text, p_entity_type text DEFAULT NULL, p_entity_id uuid DEFAULT NULL,
                             p_details jsonb DEFAULT NULL, p_result text DEFAULT 'SUCCESS') RETURNS bigint
    LANGUAGE sql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT audit_write(hms_actor_id(), p_action, p_entity_type, p_entity_id, p_details, p_result, hms_session_id())
$$;

-- Backend API: records a batch of UI events in one call (max 500).
-- p_events: [{"action":"UI_CLICK","entityType":null,"entityId":null,"details":{...},"result":"SUCCESS"}, ...]
CREATE FUNCTION audit_record_batch(p_events jsonb) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    e       jsonb;
    v_count integer := 0;
BEGIN
    IF jsonb_typeof(p_events) IS DISTINCT FROM 'array' OR jsonb_array_length(p_events) > 500 THEN
        RAISE EXCEPTION USING ERRCODE = 'HL003', MESSAGE = 'AUDIT_DETAILS_TOO_LARGE: p_events must be a JSON array of at most 500 events';
    END IF;
    FOR e IN SELECT value FROM jsonb_array_elements(p_events) LOOP
        PERFORM audit_write(hms_actor_id(), e ->> 'action', e ->> 'entityType', (e ->> 'entityId')::uuid,
                            e -> 'details', coalesce(e ->> 'result', 'SUCCESS'), hms_session_id());
        v_count := v_count + 1;
    END LOOP;
    RETURN v_count;
END
$$;

-- -----------------------------------------------------------------------------
-- 6. Automatic data-change events (before/after values, secrets excluded)
-- -----------------------------------------------------------------------------
-- Arguments: entity type code, id column, comma-separated columns to leave out.
CREATE FUNCTION audit_row_change() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_exclude text[] := string_to_array(coalesce(TG_ARGV[2], ''), ',');
    v_new     jsonb;
    v_old     jsonb;
    v_changes jsonb;
BEGIN
    IF TG_OP <> 'DELETE' THEN
        v_new := to_jsonb(NEW) - v_exclude;
    END IF;
    IF TG_OP <> 'INSERT' THEN
        v_old := to_jsonb(OLD) - v_exclude;
    END IF;

    IF TG_OP = 'INSERT' THEN
        PERFORM audit_write(hms_actor_id(), 'RECORD_CREATE', TG_ARGV[0], (to_jsonb(NEW) ->> TG_ARGV[1])::uuid,
                            jsonb_build_object('fields', (SELECT jsonb_agg(k ORDER BY k) FROM jsonb_object_keys(v_new) AS k)),
                            'SUCCESS', hms_session_id());
    ELSIF TG_OP = 'UPDATE' THEN
        SELECT jsonb_object_agg(n.key, jsonb_build_array(o.value, n.value))
          INTO v_changes
          FROM jsonb_each(v_new) n
          JOIN jsonb_each(v_old) o ON o.key = n.key
         WHERE n.value IS DISTINCT FROM o.value;
        IF v_changes IS NOT NULL THEN
            PERFORM audit_write(hms_actor_id(), 'RECORD_UPDATE', TG_ARGV[0], (to_jsonb(NEW) ->> TG_ARGV[1])::uuid,
                                jsonb_build_object('changes', v_changes), 'SUCCESS', hms_session_id());
        END IF;
    ELSIF hms_delete_reason() IS NOT NULL THEN
        -- Hard delete with a reason (e.g. complete patient deletion): the values are
        -- not copied into the immutable log, so the record is really gone.
        PERFORM audit_write(hms_actor_id(), 'RECORD_DELETE', TG_ARGV[0], (to_jsonb(OLD) ->> TG_ARGV[1])::uuid,
                            jsonb_build_object('reason', hms_delete_reason(),
                                               'fields', (SELECT jsonb_agg(k ORDER BY k) FROM jsonb_object_keys(v_old) AS k)),
                            'SUCCESS', hms_session_id());
    ELSE
        PERFORM audit_write(hms_actor_id(), 'RECORD_DELETE', TG_ARGV[0], (to_jsonb(OLD) ->> TG_ARGV[1])::uuid,
                            jsonb_build_object('deleted', v_old), 'SUCCESS', hms_session_id());
    END IF;
    RETURN NULL;
END
$$;

CREATE FUNCTION audit_staff_members() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_exclude text[] := ARRAY['role_id', 'display_name', 'created_at', 'updated_at', 'version'];
    v_changes jsonb;
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM audit_write(hms_actor_id(), 'RECORD_DELETE', 'STAFF_MEMBER', OLD.id,
                            jsonb_build_object('reason', hms_delete_reason(), 'displayName', OLD.display_name,
                                               'role', (SELECT code FROM roles WHERE id = OLD.role_id)),
                            'SUCCESS', hms_session_id());
        RETURN NULL;
    END IF;
    IF TG_OP = 'INSERT' THEN
        PERFORM audit_write(hms_actor_id(), 'RECORD_CREATE', 'STAFF_MEMBER', NEW.id,
                            jsonb_build_object(
                                'fields', (SELECT jsonb_agg(k ORDER BY k) FROM jsonb_object_keys(to_jsonb(NEW) - v_exclude) AS k),
                                'role',   (SELECT code FROM roles WHERE id = NEW.role_id)),
                            'SUCCESS', hms_session_id());
        RETURN NULL;
    END IF;
    IF NEW.role_id IS DISTINCT FROM OLD.role_id THEN
        PERFORM audit_write(hms_actor_id(), 'ACCOUNT_ROLE_CHANGED', 'STAFF_MEMBER', NEW.id,
                            jsonb_build_object('changes', jsonb_build_object('role', jsonb_build_array(
                                (SELECT code FROM roles WHERE id = OLD.role_id),
                                (SELECT code FROM roles WHERE id = NEW.role_id)))),
                            'SUCCESS', hms_session_id());
    END IF;
    SELECT jsonb_object_agg(n.key, jsonb_build_array(o.value, n.value))
      INTO v_changes
      FROM jsonb_each(to_jsonb(NEW) - v_exclude) n
      JOIN jsonb_each(to_jsonb(OLD) - v_exclude) o ON o.key = n.key
     WHERE n.value IS DISTINCT FROM o.value;
    IF v_changes IS NOT NULL THEN
        PERFORM audit_write(hms_actor_id(), 'RECORD_UPDATE', 'STAFF_MEMBER', NEW.id,
                            jsonb_build_object('changes', v_changes), 'SUCCESS', hms_session_id());
    END IF;
    RETURN NULL;
END
$$;

-- Password hashes are never written to the log; only the fact of the change.
CREATE FUNCTION audit_user_accounts() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor   uuid   := hms_actor_id();
    v_exclude text[] := ARRAY['password_hash', 'password_changed_at', 'status', 'status_changed_at',
                              'failed_login_count', 'created_at', 'updated_at', 'version'];
    v_changes jsonb;
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM audit_write(v_actor, 'ACCOUNT_DELETED', 'USER_ACCOUNT', OLD.id,
                            jsonb_build_object('reason', hms_delete_reason(), 'username', OLD.username,
                                               'staffMemberId', OLD.staff_member_id),
                            'SUCCESS', hms_session_id());
        RETURN NULL;
    END IF;
    IF TG_OP = 'INSERT' THEN
        PERFORM audit_write(v_actor, 'ACCOUNT_CREATED', 'USER_ACCOUNT', NEW.id,
                            jsonb_build_object('username', NEW.username, 'staffMemberId', NEW.staff_member_id),
                            'SUCCESS', hms_session_id());
        RETURN NULL;
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        PERFORM audit_write(v_actor,
                            CASE NEW.status WHEN 'DEACTIVATED' THEN 'ACCOUNT_DEACTIVATED' ELSE 'ACCOUNT_REACTIVATED' END,
                            'USER_ACCOUNT', NEW.id, NULL, 'SUCCESS', hms_session_id());
    END IF;
    IF NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        PERFORM audit_write(v_actor,
                            CASE WHEN NEW.id = v_actor AND NOT NEW.must_change_password
                                 THEN 'ACCOUNT_PASSWORD_CHANGED' ELSE 'ACCOUNT_PASSWORD_RESET' END,
                            'USER_ACCOUNT', NEW.id, NULL, 'SUCCESS', hms_session_id());
    END IF;
    SELECT jsonb_object_agg(n.key, jsonb_build_array(o.value, n.value))
      INTO v_changes
      FROM jsonb_each(to_jsonb(NEW) - v_exclude) n
      JOIN jsonb_each(to_jsonb(OLD) - v_exclude) o ON o.key = n.key
     WHERE n.value IS DISTINCT FROM o.value;
    IF v_changes IS NOT NULL THEN
        PERFORM audit_write(v_actor, 'RECORD_UPDATE', 'USER_ACCOUNT', NEW.id,
                            jsonb_build_object('changes', v_changes), 'SUCCESS', hms_session_id());
    END IF;
    RETURN NULL;
END
$$;

CREATE FUNCTION audit_role_permissions() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_row role_permissions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_row := OLD;
    ELSE
        v_row := NEW;
    END IF;
    PERFORM audit_write(hms_actor_id(),
                        CASE TG_OP WHEN 'DELETE' THEN 'ROLE_PERMISSION_REVOKED' ELSE 'ROLE_PERMISSION_GRANTED' END,
                        'ROLE_PERMISSION', NULL,
                        jsonb_build_object('role',       (SELECT code FROM roles WHERE id = v_row.role_id),
                                           'permission', (SELECT code FROM permissions WHERE id = v_row.permission_id)),
                        'SUCCESS', hms_session_id());
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_90_departments_audit AFTER INSERT OR UPDATE OR DELETE ON departments
    FOR EACH ROW EXECUTE FUNCTION audit_row_change('DEPARTMENT', 'id', 'created_at,updated_at,version');
CREATE TRIGGER trg_90_doctor_departments_audit AFTER INSERT OR UPDATE OR DELETE ON doctor_departments
    FOR EACH ROW EXECUTE FUNCTION audit_row_change('DOCTOR_DEPARTMENT', 'staff_member_id', 'assigned_at');
CREATE TRIGGER trg_90_staff_members_audit AFTER INSERT OR UPDATE OR DELETE ON staff_members
    FOR EACH ROW EXECUTE FUNCTION audit_staff_members();
CREATE TRIGGER trg_90_user_accounts_audit AFTER INSERT OR UPDATE OR DELETE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION audit_user_accounts();
CREATE TRIGGER trg_90_auth_sessions_audit AFTER DELETE ON auth_sessions
    FOR EACH ROW EXECUTE FUNCTION audit_row_change('AUTH_SESSION', 'id', 'token_hash,client_ip,user_agent');
CREATE TRIGGER trg_90_role_permissions_audit AFTER INSERT OR DELETE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION audit_role_permissions();

-- -----------------------------------------------------------------------------
-- 7. Login / logout lifecycle (US-01)
--    The backend verifies the password hash (BCrypt) and then calls one of:
-- -----------------------------------------------------------------------------
-- Returns the new session id, or NULL when the account is unknown/deactivated
-- (the failed attempt is still logged). A deactivated user never gets a session.
CREATE FUNCTION auth_login_success(p_account_id uuid, p_token_hash bytea, p_expires_at timestamptz,
                                   p_client_ip inet DEFAULT NULL, p_user_agent text DEFAULT NULL) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_status  text;
    v_session uuid;
BEGIN
    SELECT a.status INTO v_status FROM user_accounts a WHERE a.id = p_account_id FOR UPDATE;
    IF v_status IS NULL OR v_status <> 'ACTIVE' THEN
        PERFORM audit_write(NULL, 'LOGIN_FAILURE', 'USER_ACCOUNT', p_account_id,
                            jsonb_strip_nulls(jsonb_build_object(
                                'reason', CASE WHEN v_status IS NULL THEN 'UNKNOWN_ACCOUNT' ELSE 'ACCOUNT_DEACTIVATED' END,
                                'ip', host(p_client_ip))),
                            'DENIED', NULL);
        RETURN NULL;
    END IF;
    PERFORM set_config('hms.trusted_op', 'auth', true);
    UPDATE user_accounts SET failed_login_count = 0 WHERE id = p_account_id AND failed_login_count <> 0;
    INSERT INTO auth_sessions (user_account_id, token_hash, expires_at, client_ip, user_agent)
    VALUES (p_account_id, p_token_hash, p_expires_at, p_client_ip, left(nullif(btrim(p_user_agent), ''), 500))
    RETURNING id INTO v_session;
    PERFORM set_config('hms.trusted_op', '', true);
    PERFORM audit_write(p_account_id, 'LOGIN_SUCCESS', 'AUTH_SESSION', v_session, NULL, 'SUCCESS', v_session);
    RETURN v_session;
END
$$;

-- Wrong password or unknown username. The attempted password is never passed in.
CREATE FUNCTION auth_login_failure(p_username text, p_client_ip inet DEFAULT NULL,
                                   p_reason text DEFAULT 'BAD_CREDENTIALS') RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_account uuid;
BEGIN
    SELECT a.id INTO v_account FROM user_accounts a WHERE lower(a.username) = lower(btrim(p_username));
    IF v_account IS NOT NULL THEN
        PERFORM set_config('hms.trusted_op', 'auth', true);
        UPDATE user_accounts SET failed_login_count = failed_login_count + 1 WHERE id = v_account;
        PERFORM set_config('hms.trusted_op', '', true);
    END IF;
    PERFORM audit_write(NULL, 'LOGIN_FAILURE', 'USER_ACCOUNT', v_account,
                        jsonb_strip_nulls(jsonb_build_object(
                            'username', left(p_username, 100), 'reason', p_reason, 'ip', host(p_client_ip))),
                        'FAILURE', NULL);
END
$$;

-- Logout: the token becomes invalid immediately. Returns false if already ended.
CREATE FUNCTION auth_logout(p_session_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_account uuid;
BEGIN
    SELECT s.user_account_id INTO v_account FROM auth_sessions s WHERE s.id = p_session_id AND s.revoked_at IS NULL;
    IF v_account IS NULL THEN
        RETURN false;
    END IF;
    UPDATE auth_sessions SET revoked_at = now(), revoke_reason = 'LOGOUT' WHERE id = p_session_id;
    PERFORM audit_write(v_account, 'LOGOUT', 'AUTH_SESSION', p_session_id, NULL, 'SUCCESS', p_session_id);
    RETURN true;
END
$$;

-- Administrator ends any session (e.g. a lost device). Returns false if it already ended.
CREATE FUNCTION auth_revoke_session(p_session_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor   uuid := hms_require_permission('STAFF_ACCOUNT_MANAGE');
    v_account uuid;
BEGIN
    SELECT s.user_account_id INTO v_account FROM auth_sessions s WHERE s.id = p_session_id AND s.revoked_at IS NULL;
    IF v_account IS NULL THEN
        RETURN false;
    END IF;
    UPDATE auth_sessions SET revoked_at = now(), revoke_reason = 'ADMIN_REVOKED' WHERE id = p_session_id;
    PERFORM audit_write(v_actor, 'SESSION_REVOKED', 'AUTH_SESSION', p_session_id,
                        jsonb_build_object('accountId', v_account), 'SUCCESS', hms_session_id());
    RETURN true;
END
$$;

-- Per-request token check: returns one row only for a live session of an active account.
CREATE FUNCTION auth_validate_session(p_token_hash bytea)
    RETURNS TABLE (session_id uuid, account_id uuid, staff_member_id uuid, role text,
                   must_change_password boolean, expires_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT s.id, a.id, a.staff_member_id, r.code, a.must_change_password, s.expires_at
    FROM auth_sessions s
    JOIN user_accounts a ON a.id = s.user_account_id
    JOIN staff_members m ON m.id = a.staff_member_id
    JOIN roles r ON r.id = m.role_id
    WHERE s.token_hash = p_token_hash
      AND s.revoked_at IS NULL
      AND s.expires_at > now()
      AND a.status = 'ACTIVE'
$$;

CREATE FUNCTION auth_change_own_password(p_new_password_hash text) RETURNS void
    LANGUAGE plpgsql
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_actor uuid := hms_require_actor();
BEGIN
    UPDATE user_accounts
       SET password_hash = p_new_password_hash, must_change_password = false
     WHERE id = v_actor;
END
$$;

-- Housekeeping: marks expired sessions as ended (validation already ignores them).
CREATE FUNCTION auth_expire_sessions() RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_count integer;
BEGIN
    UPDATE auth_sessions SET revoked_at = now(), revoke_reason = 'EXPIRED'
     WHERE revoked_at IS NULL AND expires_at <= now();
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END
$$;

-- -----------------------------------------------------------------------------
-- 8. Reading the log (administrators only; the read itself is logged)
-- -----------------------------------------------------------------------------
CREATE FUNCTION audit_search(p_actor_user_id uuid        DEFAULT NULL,
                             p_action        text        DEFAULT NULL,
                             p_from          timestamptz DEFAULT NULL,
                             p_to            timestamptz DEFAULT NULL,
                             p_entity_type   text        DEFAULT NULL,
                             p_entity_id     uuid        DEFAULT NULL,
                             p_limit         integer     DEFAULT 100,
                             p_offset        integer     DEFAULT 0)
    RETURNS TABLE (id bigint, occurred_at timestamptz, actor_user_id uuid, actor_username text,
                   actor_role text, action text, action_category text, result text,
                   entity_type text, entity_id uuid, session_id uuid, details jsonb,
                   source text, total_count bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
#variable_conflict use_column
DECLARE
    v_actor uuid := hms_require_permission('AUDIT_LOG_VIEW');
BEGIN
    IF p_limit IS NULL OR p_limit < 1 OR p_limit > 1000 OR p_offset IS NULL OR p_offset < 0 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'p_limit must be 1..1000 and p_offset >= 0';
    END IF;
    PERFORM audit_write(v_actor, 'AUDIT_LOG_VIEWED', 'AUDIT_LOG', NULL,
                        jsonb_strip_nulls(jsonb_build_object(
                            'actorUserId', p_actor_user_id, 'action', p_action, 'from', p_from, 'to', p_to,
                            'entityType', p_entity_type, 'entityId', p_entity_id,
                            'limit', p_limit, 'offset', p_offset)),
                        'SUCCESS', hms_session_id());
    RETURN QUERY
    WITH matched AS (
        SELECT l.id, l.occurred_at, l.actor_user_id, a.username AS actor_username, r.code AS actor_role,
               act.code AS action, act.category AS action_category,
               CASE l.result WHEN 1 THEN 'SUCCESS' WHEN 2 THEN 'FAILURE' ELSE 'DENIED' END AS result,
               et.code AS entity_type, l.entity_id, l.session_id, l.details, 'LIVE'::text AS source
        FROM audit_log l
        JOIN audit_actions act           ON act.id = l.action_id
        LEFT JOIN user_accounts a        ON a.id = l.actor_user_id
        LEFT JOIN roles r                ON r.id = l.actor_role_id
        LEFT JOIN audit_entity_types et  ON et.id = l.entity_type_id
        WHERE (p_actor_user_id IS NULL OR l.actor_user_id = p_actor_user_id)
          AND (p_action        IS NULL OR act.code = p_action)
          AND (p_from          IS NULL OR l.occurred_at >= p_from)
          AND (p_to            IS NULL OR l.occurred_at <  p_to)
          AND (p_entity_type   IS NULL OR et.code = p_entity_type)
          AND (p_entity_id     IS NULL OR l.entity_id = p_entity_id)
        UNION ALL
        SELECT x.id, x.occurred_at, x.actor_user_id, x.actor_username, x.actor_role,
               x.action, x.action_category, x.result, x.entity_type, x.entity_id, x.session_id, x.details,
               'ARCHIVE'::text
        FROM audit_log_restored x
        WHERE (p_actor_user_id IS NULL OR x.actor_user_id = p_actor_user_id)
          AND (p_action        IS NULL OR x.action = p_action)
          AND (p_from          IS NULL OR x.occurred_at >= p_from)
          AND (p_to            IS NULL OR x.occurred_at <  p_to)
          AND (p_entity_type   IS NULL OR x.entity_type = p_entity_type)
          AND (p_entity_id     IS NULL OR x.entity_id = p_entity_id)
    )
    SELECT m.id, m.occurred_at, m.actor_user_id, m.actor_username, m.actor_role, m.action,
           m.action_category, m.result, m.entity_type, m.entity_id, m.session_id, m.details,
           m.source, count(*) OVER ()
    FROM matched m
    ORDER BY m.occurred_at DESC, m.id DESC
    LIMIT p_limit OFFSET p_offset;
END
$$;

-- -----------------------------------------------------------------------------
-- 9. Archiving (operator functions, used by scripts/audit_archive.py)
-- -----------------------------------------------------------------------------
-- VOLATILE on purpose: the planner must never skip this check inside an export query.
CREATE FUNCTION audit_assert_closed_period(p_period date) RETURNS text
    LANGUAGE plpgsql VOLATILE
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_name text := audit_partition_name(p_period);
BEGIN
    IF p_period IS NULL OR extract(day FROM p_period) <> 1
       OR p_period >= date_trunc('month', now() AT TIME ZONE 'UTC')::date THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004',
            MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is not a closed month (use the first day of a past month)', p_period);
    END IF;
    IF to_regclass('public.' || v_name) IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004',
            MESSAGE = format('AUDIT_ARCHIVE_STATE: partition %s does not exist', v_name);
    END IF;
    RETURN v_name;
END
$$;

CREATE FUNCTION audit_archive_snapshot(p_period date)
    RETURNS TABLE (partition_name text, row_count bigint, min_id bigint, max_id bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_name text := audit_assert_closed_period(p_period);
BEGIN
    RETURN QUERY EXECUTE format(
        'SELECT %L::text, count(*), min(id), max(id) FROM public.%I',
        v_name, v_name);
END
$$;

-- Rows in archive form (readable codes, UTC timestamps). A view, not a
-- function, so COPY streams the rows and the month filter prunes partitions.
-- Used by scripts/audit_archive.py together with audit_assert_closed_period():
--   COPY (SELECT id, occurred_at_utc, ... FROM audit_log_export
--         CROSS JOIN (SELECT audit_assert_closed_period(:month) AS partition) closed
--         WHERE closed.partition IS NOT NULL AND occurred_at >= :from AND occurred_at < :to
--         ORDER BY id) TO STDOUT
CREATE VIEW audit_log_export AS
SELECT l.id,
       to_char(l.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS occurred_at_utc,
       l.actor_user_id,
       a.username   AS actor_username,
       r.code       AS actor_role,
       act.code     AS action,
       act.category AS action_category,
       CASE l.result WHEN 1 THEN 'SUCCESS' WHEN 2 THEN 'FAILURE' ELSE 'DENIED' END AS result,
       et.code      AS entity_type,
       l.entity_id,
       l.session_id,
       l.details,
       l.occurred_at
FROM audit_log l
JOIN audit_actions act          ON act.id = l.action_id
LEFT JOIN user_accounts a       ON a.id = l.actor_user_id
LEFT JOIN roles r               ON r.id = l.actor_role_id
LEFT JOIN audit_entity_types et ON et.id = l.entity_type_id;

CREATE FUNCTION audit_archive_register(p_period date, p_file_name text, p_row_count bigint,
                                       p_min_id bigint, p_max_id bigint, p_file_sha256 text,
                                       p_content_sha256 text, p_file_bytes bigint, p_content_bytes bigint,
                                       p_compression text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    s record;
BEGIN
    IF EXISTS (SELECT 1 FROM audit_archives a WHERE a.period_start = p_period) THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is already archived', p_period);
    END IF;
    SELECT * INTO s FROM audit_archive_snapshot(p_period);
    IF s.row_count IS DISTINCT FROM p_row_count OR s.min_id IS DISTINCT FROM p_min_id
       OR s.max_id IS DISTINCT FROM p_max_id THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004',
            MESSAGE = 'AUDIT_ARCHIVE_STATE: archive file does not match the partition',
            DETAIL  = format('partition rows=%s ids=%s..%s, file rows=%s ids=%s..%s',
                             s.row_count, s.min_id, s.max_id, p_row_count, p_min_id, p_max_id);
    END IF;
    PERFORM set_config('hms.trusted_op', 'archive', true);
    INSERT INTO audit_archives (period_start, partition_name, row_count, min_id, max_id, file_name,
                                file_sha256, content_sha256, file_bytes, content_bytes, compression)
    VALUES (p_period, s.partition_name, p_row_count, p_min_id, p_max_id, p_file_name,
            lower(p_file_sha256), lower(p_content_sha256), p_file_bytes, p_content_bytes, p_compression);
    PERFORM set_config('hms.trusted_op', '', true);
    PERFORM audit_write(NULL, 'AUDIT_ARCHIVE_EXPORTED', 'AUDIT_LOG', NULL,
                        jsonb_build_object('period', p_period, 'rows', p_row_count, 'file', p_file_name,
                                           'fileBytes', p_file_bytes, 'contentBytes', p_content_bytes,
                                           'sha256', lower(p_file_sha256)),
                        'SUCCESS', NULL);
END
$$;

-- Removes an archived month from the live database (the archive file stays the
-- authoritative copy). Only possible for verified archives.
CREATE FUNCTION audit_archive_drop_partition(p_period date) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_archive audit_archives%ROWTYPE;
    s         record;
BEGIN
    SELECT * INTO v_archive FROM audit_archives a WHERE a.period_start = p_period;
    IF v_archive.period_start IS NULL OR v_archive.status <> 'ARCHIVED' THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004',
            MESSAGE = format('AUDIT_ARCHIVE_STATE: %s has no verified archive or is already dropped', p_period);
    END IF;
    SELECT * INTO s FROM audit_archive_snapshot(p_period);
    IF s.row_count IS DISTINCT FROM v_archive.row_count OR s.min_id IS DISTINCT FROM v_archive.min_id
       OR s.max_id IS DISTINCT FROM v_archive.max_id THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = 'AUDIT_ARCHIVE_STATE: partition differs from its archive';
    END IF;
    PERFORM set_config('hms.audit_drop_partition', v_archive.partition_name, true);
    EXECUTE format('DROP TABLE public.%I', v_archive.partition_name);
    PERFORM set_config('hms.audit_drop_partition', '', true);
    PERFORM set_config('hms.trusted_op', 'archive', true);
    UPDATE audit_archives SET status = 'PARTITION_DROPPED', status_changed_at = now() WHERE period_start = p_period;
    PERFORM set_config('hms.trusted_op', '', true);
    PERFORM audit_write(NULL, 'AUDIT_PARTITION_DROPPED', 'AUDIT_LOG', NULL,
                        jsonb_build_object('period', p_period, 'rows', v_archive.row_count,
                                           'file', v_archive.file_name),
                        'SUCCESS', NULL);
    RETURN v_archive.row_count;
END
$$;

-- Restore runs in ONE transaction (psql --single-transaction):
--   SELECT audit_restore_begin(:period);
--   COPY audit_log_restored (id, occurred_at, ..., details) FROM STDIN;   -- period_start comes from begin
--   SELECT audit_restore_finish(:period);
-- begin checks the archive state and opens the write window; finish verifies
-- row count and id range against the archive record (otherwise everything is
-- rolled back), closes the window and logs the restore.
CREATE FUNCTION audit_restore_begin(p_period date) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_archive audit_archives%ROWTYPE;
BEGIN
    SELECT * INTO v_archive FROM audit_archives a WHERE a.period_start = p_period;
    IF v_archive.period_start IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is not archived', p_period);
    END IF;
    IF v_archive.status <> 'PARTITION_DROPPED' THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004',
            MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is still in the live log; use audit_search()', p_period);
    END IF;
    IF EXISTS (SELECT 1 FROM audit_log_restored r WHERE r.period_start = p_period) THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = format('AUDIT_ARCHIVE_STATE: %s is already restored', p_period);
    END IF;
    PERFORM set_config('hms.trusted_op', 'archive', true);
    PERFORM set_config('hms.restore_period', p_period::text, true);
END
$$;

CREATE FUNCTION audit_restore_finish(p_period date) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_archive audit_archives%ROWTYPE;
    v_rows    bigint;
    v_min     bigint;
    v_max     bigint;
BEGIN
    IF hms_trusted_op() <> 'archive' OR current_setting('hms.restore_period', true) IS DISTINCT FROM p_period::text THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = 'AUDIT_ARCHIVE_STATE: call audit_restore_begin() first in the same transaction';
    END IF;
    SELECT * INTO v_archive FROM audit_archives a WHERE a.period_start = p_period;
    SELECT count(*), min(id), max(id) INTO v_rows, v_min, v_max
      FROM audit_log_restored r WHERE r.period_start = p_period;
    IF v_rows IS DISTINCT FROM v_archive.row_count OR v_min IS DISTINCT FROM v_archive.min_id
       OR v_max IS DISTINCT FROM v_archive.max_id THEN
        RAISE EXCEPTION USING ERRCODE = 'HL004', MESSAGE = 'AUDIT_ARCHIVE_STATE: restored rows do not match the archive record';
    END IF;
    PERFORM set_config('hms.trusted_op', '', true);
    PERFORM set_config('hms.restore_period', '', true);
    PERFORM audit_write(NULL, 'AUDIT_ARCHIVE_RESTORED', 'AUDIT_LOG', NULL,
                        jsonb_build_object('period', p_period, 'rows', v_rows), 'SUCCESS', NULL);
    RETURN v_rows;
END
$$;

-- Removes a restored copy again (the archive file is not touched).
CREATE FUNCTION audit_restore_unload(p_period date) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    v_rows bigint;
BEGIN
    PERFORM set_config('hms.trusted_op', 'archive', true);
    DELETE FROM audit_log_restored WHERE period_start = p_period;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    PERFORM set_config('hms.trusted_op', '', true);
    PERFORM audit_write(NULL, 'AUDIT_RESTORE_UNLOADED', 'AUDIT_LOG', NULL,
                        jsonb_build_object('period', p_period, 'rows', v_rows), 'SUCCESS', NULL);
    RETURN v_rows;
END
$$;

-- Overview for operators: live partitions and archived months with their size.
CREATE FUNCTION audit_partitions()
    RETURNS TABLE (period_start date, partition_name text, live boolean, live_bytes bigint,
                   archive_status text, archived_rows bigint, archive_file text, archive_bytes bigint)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    WITH live AS (
        SELECT to_date(substr(c.relname, 12, 4) || substr(c.relname, 17, 2), 'YYYYMM') AS period_start,
               c.relname::text AS partition_name,
               pg_total_relation_size(c.oid) AS live_bytes
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'public.audit_log'::regclass
    )
    -- NULL = not applicable (no live partition / not archived yet)
    SELECT coalesce(l.period_start, a.period_start),
           coalesce(l.partition_name, a.partition_name),
           l.partition_name IS NOT NULL,
           l.live_bytes,
           a.status,
           a.row_count,
           a.file_name,
           a.file_bytes
    FROM live l
    FULL JOIN audit_archives a ON a.period_start = l.period_start
    ORDER BY 1
$$;

-- -----------------------------------------------------------------------------
-- 10. Event triggers: no DROP of audit tables, no disabled/missing audit triggers
-- -----------------------------------------------------------------------------
CREATE FUNCTION audit_protect_drop() RETURNS event_trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    o record;
BEGIN
    FOR o IN SELECT * FROM pg_event_trigger_dropped_objects() LOOP
        IF o.object_type = 'table' AND o.schema_name = 'public'
           AND (o.object_name IN ('audit_log', 'audit_archives', 'audit_log_restored', 'audit_actions',
                                  'audit_entity_types', 'record_deletions')
                OR o.object_name ~ '^audit_log_y[0-9]{4}m[0-9]{2}$') THEN
            IF o.object_name ~ '^audit_log_y[0-9]{4}m[0-9]{2}$'
               AND o.object_name = current_setting('hms.audit_drop_partition', true) THEN
                CONTINUE;
            END IF;
            RAISE EXCEPTION USING
                ERRCODE = 'HL001',
                MESSAGE = format('AUDIT_IMMUTABLE: dropping %s is not allowed', o.object_name);
        END IF;
    END LOOP;
END
$$;

CREATE FUNCTION audit_protect_triggers() RETURNS event_trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_bad text;
BEGIN
    IF to_regclass('public.audit_log') IS NULL THEN
        RETURN;
    END IF;
    SELECT string_agg(c.relname || '.' || t.tgname, ', ')
      INTO v_bad
      FROM pg_trigger t
      JOIN pg_class c ON c.oid = t.tgrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND (c.relname IN ('audit_log', 'audit_archives', 'audit_log_restored')
            OR c.relname ~ '^audit_log_y[0-9]{4}m[0-9]{2}$')
       AND t.tgname LIKE 'trg\_audit%'
       AND t.tgenabled = 'D';
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL001', MESSAGE = 'AUDIT_IMMUTABLE: audit triggers cannot be disabled: ' || v_bad;
    END IF;
    SELECT string_agg(c.relname, ', ')
      INTO v_bad
      FROM pg_inherits i
      JOIN pg_class c ON c.oid = i.inhrelid
     WHERE i.inhparent = 'public.audit_log'::regclass
       AND c.relname IS DISTINCT FROM current_setting('hms.audit_drop_partition', true)
       AND NOT (EXISTS (SELECT 1 FROM pg_trigger t WHERE t.tgrelid = c.oid AND t.tgname = 'trg_audit_log_immutable')
                AND EXISTS (SELECT 1 FROM pg_trigger t WHERE t.tgrelid = c.oid AND t.tgname = 'trg_audit_log_truncate'));
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'HL001', MESSAGE = 'AUDIT_IMMUTABLE: audit partition triggers missing: ' || v_bad;
    END IF;
END
$$;

DO $$
BEGIN
    IF (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        CREATE EVENT TRIGGER audit_protect_drop ON sql_drop
            EXECUTE FUNCTION audit_protect_drop();
        CREATE EVENT TRIGGER audit_protect_triggers ON ddl_command_end
            WHEN TAG IN ('ALTER TABLE', 'DROP TRIGGER')
            EXECUTE FUNCTION audit_protect_triggers();
    ELSE
        RAISE NOTICE 'Event triggers not created (superuser required); row/statement triggers and privileges still protect the audit log.';
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 11. Permission, partitions, privileges
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, module, story, description_tr, description_en) VALUES
(4, 'AUDIT_LOG_VIEW', 'security', 'US-03, US-04', 'Log kayıtlarını görüntüleme ve filtreleme', 'View and filter the audit log');

SELECT audit_maintain_partitions(3);

REVOKE EXECUTE ON FUNCTION audit_write(uuid, text, text, uuid, jsonb, text, uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_ensure_partition(timestamptz) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_archive_snapshot(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_assert_closed_period(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_archive_register(date, text, bigint, bigint, bigint, text, text, bigint, bigint, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_archive_drop_partition(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_restore_begin(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_restore_finish(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_restore_unload(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_partitions() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_record(text, text, uuid, jsonb, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_record_batch(jsonb) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_search(uuid, text, timestamptz, timestamptz, text, uuid, integer, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_maintain_partitions(integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_login_success(uuid, bytea, timestamptz, inet, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_login_failure(text, inet, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_logout(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_revoke_session(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_validate_session(bytea) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_expire_sessions() FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hms_app') THEN
        GRANT SELECT ON audit_actions, audit_entity_types TO hms_app;
        GRANT EXECUTE ON FUNCTION audit_record(text, text, uuid, jsonb, text),
                                  audit_record_batch(jsonb),
                                  audit_search(uuid, text, timestamptz, timestamptz, text, uuid, integer, integer),
                                  audit_maintain_partitions(integer),
                                  auth_login_success(uuid, bytea, timestamptz, inet, text),
                                  auth_login_failure(text, inet, text),
                                  auth_logout(uuid),
                                  auth_revoke_session(uuid),
                                  auth_validate_session(bytea),
                                  auth_expire_sessions()
              TO hms_app;
    END IF;
END
$$;

SELECT set_config('hms.trusted_op', '', false);
