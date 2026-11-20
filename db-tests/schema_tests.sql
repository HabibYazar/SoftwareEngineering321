-- =============================================================================
-- HMS Community | schema acceptance tests for V1..V3
-- Run against a freshly migrated database as the migration owner:
--     psql -X -v ON_ERROR_STOP=1 -f db-tests/schema_tests.sql
-- Everything runs in ONE transaction that is rolled back at the end, so the
-- database is left exactly as the migrations created it (no test data kept).
-- =============================================================================
\set ON_ERROR_STOP 1
\pset pager off
-- Intermediate statements print nothing useful; the result table comes at the end.
\pset tuples_only on
\pset footer off
BEGIN;

CREATE SCHEMA hms_test;
GRANT USAGE ON SCHEMA hms_test TO hms_app;
CREATE TABLE hms_test.results (n serial PRIMARY KEY, story text, label text, passed boolean, info text);
GRANT SELECT, INSERT ON hms_test.results TO hms_app;
GRANT USAGE ON SEQUENCE hms_test.results_n_seq TO hms_app;

CREATE FUNCTION hms_test.ok(p_story text, p_pass boolean, p_label text, p_info text DEFAULT '') RETURNS void
    LANGUAGE sql
AS $$ INSERT INTO hms_test.results (story, label, passed, info) VALUES (p_story, p_label, coalesce(p_pass, false), coalesce(p_info, '')) $$;

-- Runs p_sql (deferred constraints are fired too) and expects SQLSTATE p_state.
-- Optionally checks the COLUMN diagnostic field and a LIKE pattern on DETAIL.
CREATE FUNCTION hms_test.expect_error(p_story text, p_sql text, p_state text, p_label text,
                                      p_column text DEFAULT NULL, p_detail_like text DEFAULT NULL) RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    v_state  text;
    v_column text;
    v_type   text;
    v_detail text;
    v_msg    text;
BEGIN
    BEGIN
        EXECUTE p_sql;
        EXECUTE 'SET CONSTRAINTS ALL IMMEDIATE';
        RAISE EXCEPTION USING ERRCODE = 'ZZ999', MESSAGE = 'no error raised';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state  = RETURNED_SQLSTATE,
                                v_column = COLUMN_NAME,
                                v_type   = PG_DATATYPE_NAME,
                                v_detail = PG_EXCEPTION_DETAIL,
                                v_msg    = MESSAGE_TEXT;
    END;
    EXECUTE 'SET CONSTRAINTS ALL DEFERRED';
    PERFORM hms_test.ok(p_story,
        v_state = p_state
        AND (p_column IS NULL OR v_column = p_column)
        AND (p_detail_like IS NULL OR v_detail LIKE p_detail_like),
        p_label,
        format('state=%s column=%s type=%s msg=%s detail=%s', v_state, v_column, v_type, v_msg, left(v_detail, 400)));
END
$$;

CREATE FUNCTION hms_test.uuid(p_key text) RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT current_setting('hms_test.' || p_key)::uuid $$;

CREATE FUNCTION hms_test.act(p_key text, p_session text DEFAULT NULL) RETURNS void
    LANGUAGE sql
AS $$ SELECT hms_set_context(hms_test.uuid(p_key), CASE WHEN p_session IS NULL THEN NULL ELSE hms_test.uuid(p_session) END) $$;

CREATE FUNCTION hms_test.hash(p_char text) RETURNS text
    LANGUAGE sql IMMUTABLE
AS $$ SELECT '$2a$10$' || repeat(p_char, 53) $$;

CREATE FUNCTION hms_test.new_account(p_key text, p_role text, p_first text, p_last text, p_username text) RETURNS uuid
    LANGUAGE plpgsql
AS $$
DECLARE
    v_staff   uuid;
    v_account uuid;
BEGIN
    INSERT INTO staff_members (role_id, first_name, last_name)
    SELECT id, p_first, p_last FROM roles WHERE code = p_role
    RETURNING id INTO v_staff;
    INSERT INTO user_accounts (staff_member_id, username, password_hash)
    VALUES (v_staff, p_username, hms_test.hash('c'))
    RETURNING id INTO v_account;
    PERFORM set_config('hms_test.' || p_key, v_account::text, true);
    PERFORM set_config('hms_test.' || p_key || '_staff', v_staff::text, true);
    RETURN v_account;
END
$$;

-- Convenience: the last audit row matching an action
CREATE FUNCTION hms_test.last_event(p_action text)
    RETURNS TABLE (actor uuid, entity uuid, details jsonb, result smallint)
    LANGUAGE sql STABLE
AS $$
    SELECT l.actor_user_id, l.entity_id, l.details, l.result
    FROM audit_log l JOIN audit_actions a ON a.id = l.action_id
    WHERE a.code = p_action
    ORDER BY l.id DESC LIMIT 1
$$;

-- ============================================================================
-- A. Conventions and reference data
-- ============================================================================
SELECT hms_test.ok('NFR-04', (SELECT count(*) FROM staff_members) + (SELECT count(*) FROM user_accounts)
                           + (SELECT count(*) FROM patients) + (SELECT count(*) FROM departments) = 0,
                   'Migrations contain no person, patient or department rows (no dummy data)');
SELECT hms_test.ok('US-02', (SELECT string_agg(code, ',' ORDER BY id) FROM roles)
                   = 'Administrator,Receptionist,Physician,Nurse,PatientCareAssistant,LabTechnician,CleaningStaff',
                   'Seven roles match the OpenAPI LoginResponse.role enum');
SELECT hms_test.ok('US-06', NOT EXISTS (
           SELECT 1 FROM field_rules r
           JOIN information_schema.columns c ON c.table_schema = 'public'
                                            AND c.table_name = r.table_name AND c.column_name = r.column_name
           WHERE (r.required AND c.is_nullable = 'YES') OR (NOT r.required AND c.is_nullable = 'NO')),
       'Required fields are NOT NULL, optional fields are NULL when not provided');
SELECT hms_test.ok('US-06', NOT EXISTS (
           SELECT 1 FROM information_schema.columns c
           WHERE c.table_schema = 'public' AND c.column_default IN ('''''::text', '0', '''{}''::text[]')
             AND c.table_name IN ('patients', 'patient_relatives', 'staff_members', 'departments', 'user_accounts',
                                  'auth_sessions', 'doctor_departments', 'field_rules', 'audit_archives')
             AND c.column_name NOT IN ('version', 'failed_login_count')),
       'No column uses '''' or 0 as a placeholder for "no value" (only real counters default to 0)',
       (SELECT string_agg(c.table_name || '.' || c.column_name, ', ') FROM information_schema.columns c
        WHERE c.table_schema = 'public' AND c.column_default IN ('''''::text', '0', '''{}''::text[]')
          AND c.column_name NOT IN ('version', 'failed_login_count')));
SELECT hms_test.ok('US-06', NOT EXISTS (SELECT 1 FROM field_rules
                                        WHERE max_length = 0 OR pattern = '' OR allowed_values = '{}'
                                           OR min_value = '' OR max_value = '' OR unique_mode = ''),
                   'Validation catalogue uses NULL for "no rule" (no 0 / '''' placeholders)');
SELECT hms_test.ok('US-06', (SELECT count(*) FROM field_rules r
                             JOIN pg_attribute a ON a.attrelid = ('public.' || r.table_name)::regclass
                                                AND a.attname = r.column_name
                                                AND format_type(a.atttypid, a.atttypmod) = r.data_type)
                            = (SELECT count(*) FROM field_rules),
                   'field_rules.data_type equals the real column type for every rule');
SELECT hms_test.ok('US-02', (SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                             WHERE r.code = 'Administrator') = (SELECT count(*) FROM permissions),
                   'Administrator holds every catalogued permission');
SELECT hms_test.ok('US-08', (SELECT string_agg(p.code, ',' ORDER BY p.code) FROM role_permissions rp
                             JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
                             WHERE r.code = 'Receptionist') = 'PATIENT_CREATE,PATIENT_UPDATE,PATIENT_VIEW',
                   'Receptionist may register, view and update patients');

-- ============================================================================
-- B. Accounts, roles and administration (US-01, US-05)
-- ============================================================================
SELECT set_config('hms_test.admin', security_bootstrap_admin('Test', 'Yonetici', 'Admin.One', hms_test.hash('a'))::text, true);
SELECT hms_test.ok('US-01', (SELECT username FROM user_accounts WHERE id = hms_test.uuid('admin')) = 'admin.one',
                   'Bootstrap creates the first administrator (username normalised to lower case)');
SELECT hms_test.expect_error('US-01', $$SELECT security_bootstrap_admin('X', 'Y', 'admin.two', hms_test.hash('b'))$$,
                             'HS008', 'Bootstrap works only while no administrator exists');

SELECT hms_set_context(NULL, NULL);
SELECT hms_test.expect_error('US-03', $$INSERT INTO departments (name) VALUES ('Kardiyoloji')$$,
                             'HA401', 'Writes without an acting user are rejected (every change is attributable)');

SELECT hms_test.act('admin');
SELECT hms_test.new_account('rec',   'Receptionist', 'Test', 'Resepsiyon', 'rec.one');
SELECT hms_test.new_account('doc',   'Physician',    'Test', 'Hekim',      'doc.one');
SELECT hms_test.new_account('nurse', 'Nurse',        'Test', 'Hemsire',    'nurse.one');
SELECT hms_test.new_account('admin2','Administrator','Test', 'Yonetici2',  'admin.two');

SELECT hms_test.ok('US-02', has_permission(hms_test.uuid('admin'), 'ANYTHING_AT_ALL'), 'Administrator has every permission');
SELECT hms_test.ok('US-08', has_permission(hms_test.uuid('rec'), 'PATIENT_UPDATE'), 'Receptionist has PATIENT_UPDATE');
SELECT hms_test.ok('US-02', NOT has_permission(hms_test.uuid('rec'), 'STAFF_ACCOUNT_MANAGE'), 'Receptionist cannot manage staff');

SELECT hms_test.act('rec');
SELECT hms_test.expect_error('US-05', $$INSERT INTO departments (name) VALUES ('Kardiyoloji')$$,
                             'HA403', 'Only administrators can add departments');
SELECT hms_test.expect_error('US-05', $$INSERT INTO staff_members (role_id, first_name, last_name) VALUES (2, 'A', 'B')$$,
                             'HA403', 'Only administrators can create staff');

SELECT hms_test.act('admin');
INSERT INTO departments (name) VALUES ('Kardiyoloji') RETURNING set_config('hms_test.dept', id::text, true);
SELECT hms_test.expect_error('US-05', $$INSERT INTO departments (name) VALUES ('kardiyoloji')$$,
                             'HV001', 'Department names are unique (case-insensitive)', 'name', '%DUPLICATE%');
SELECT hms_test.expect_error('US-05', $$INSERT INTO departments (name) VALUES ('   ')$$,
                             'HV001', 'Blank department name is rejected with its column', 'name', '%REQUIRED%');

INSERT INTO doctor_departments (staff_member_id, department_id) VALUES (hms_test.uuid('doc_staff'), hms_test.uuid('dept'));
SELECT hms_test.ok('US-09', (SELECT count(*) FROM doctors WHERE department = 'Kardiyoloji') = 1,
                   'Assigned physician appears in the doctors view with the department');
SELECT hms_test.expect_error('US-05', $$INSERT INTO doctor_departments (staff_member_id, department_id)
                                        VALUES (hms_test.uuid('rec_staff'), hms_test.uuid('dept'))$$,
                             'HS004', 'Only physicians can be assigned to departments');
UPDATE staff_members SET role_id = 4 WHERE id = hms_test.uuid('doc_staff');
SELECT hms_test.ok('US-05', NOT EXISTS (SELECT 1 FROM doctor_departments WHERE staff_member_id = hms_test.uuid('doc_staff'))
                        AND (SELECT entity FROM hms_test.last_event('RECORD_DELETE')) = hms_test.uuid('doc_staff'),
                   'Administrator changes a physician''s role; the department assignment is removed and logged');
UPDATE staff_members SET role_id = 3 WHERE id = hms_test.uuid('doc_staff');
INSERT INTO doctor_departments (staff_member_id, department_id) VALUES (hms_test.uuid('doc_staff'), hms_test.uuid('dept'));

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'Nurse' AND p.code = 'PATIENT_VIEW';
SELECT hms_test.ok('US-05', has_permission(hms_test.uuid('nurse'), 'PATIENT_VIEW'), 'Administrator can grant a permission to a role');
DELETE FROM role_permissions rp USING roles r, permissions p
 WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.code = 'Nurse' AND p.code = 'PATIENT_VIEW';
SELECT hms_test.ok('US-05', NOT has_permission(hms_test.uuid('nurse'), 'PATIENT_VIEW'), 'Administrator can revoke a permission');
SELECT hms_test.ok('US-03', (SELECT details ->> 'permission' FROM hms_test.last_event('ROLE_PERMISSION_REVOKED')) = 'PATIENT_VIEW'
                        AND (SELECT actor FROM hms_test.last_event('ROLE_PERMISSION_REVOKED')) = hms_test.uuid('admin'),
                   'Matrix changes are logged with role, permission and actor');
SELECT hms_test.expect_error('US-05', $$DELETE FROM role_permissions WHERE role_id = 1$$,
                             'HS005', 'Administrator permissions cannot be removed');

SELECT hms_test.expect_error('US-05', $$UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = hms_test.uuid('admin')$$,
                             'HS002', 'Administrator cannot deactivate their own account');
UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = hms_test.uuid('admin2');
SELECT hms_test.ok('US-03', (SELECT entity FROM hms_test.last_event('ACCOUNT_DEACTIVATED')) = hms_test.uuid('admin2'),
                   'Deactivation is logged');
SELECT hms_test.expect_error('US-05', $$UPDATE staff_members SET role_id = 2 WHERE id = (SELECT staff_member_id FROM user_accounts WHERE id = hms_test.uuid('admin'))$$,
                             'HS001', 'The last active administrator cannot lose the Administrator role');
UPDATE user_accounts SET status = 'ACTIVE' WHERE id = hms_test.uuid('admin2');
SELECT hms_test.act('admin2');
SELECT hms_test.ok('US-05', (SELECT count(*) FROM user_accounts a JOIN staff_members s ON s.id = a.staff_member_id
                             WHERE s.role_id = 1 AND a.status = 'ACTIVE') = 2, 'Second administrator is active again');
UPDATE staff_members SET role_id = 2 WHERE id = (SELECT staff_member_id FROM user_accounts WHERE id = hms_test.uuid('admin'));
SELECT hms_test.ok('US-03', (SELECT details -> 'changes' -> 'role' FROM hms_test.last_event('ACCOUNT_ROLE_CHANGED'))
                            = '["Administrator", "Receptionist"]'::jsonb, 'Role change is logged with before/after role');
UPDATE staff_members SET role_id = 1 WHERE id = (SELECT staff_member_id FROM user_accounts WHERE id = hms_test.uuid('admin'));
SELECT hms_test.act('admin');

UPDATE user_accounts SET username = 'rec.renamed' WHERE id = hms_test.uuid('rec');
SELECT hms_test.ok('US-05', (SELECT details -> 'changes' -> 'username' FROM hms_test.last_event('RECORD_UPDATE'))
                            = '["rec.one", "rec.renamed"]'::jsonb,
                   'Administrator can change a username (logged with before/after)');
UPDATE user_accounts SET username = 'rec.one' WHERE id = hms_test.uuid('rec');
SELECT hms_test.act('rec');
SELECT hms_test.expect_error('US-05', $$UPDATE user_accounts SET username = 'someone.else' WHERE id = hms_test.uuid('rec')$$,
                             'HA403', 'Other roles cannot change their username');
SELECT hms_test.act('admin');
SELECT hms_test.expect_error('US-05', $$SELECT hms_test.new_account('x', 'Nurse', 'A', 'B', 'REC.ONE')$$,
                             'HV001', 'Usernames are unique (case-insensitive)', 'username', '%DUPLICATE%');
SELECT hms_test.expect_error('US-01', $$UPDATE user_accounts SET password_hash = 'plain-text-password' WHERE id = hms_test.uuid('rec')$$,
                             'HV001', 'Plaintext passwords are rejected (only BCrypt/Argon2 hashes)', 'password_hash', '%INVALID_FORMAT%');
SELECT hms_test.expect_error('US-05', $$UPDATE staff_members SET first_name = '' WHERE id = hms_test.uuid('nurse_staff')$$,
                             'HV001', 'Staff details are validated on edit', 'first_name', '%REQUIRED%');
UPDATE staff_members SET title = '   ', phone = '' WHERE id = hms_test.uuid('doc_staff');
SELECT hms_test.ok('US-06', (SELECT title IS NULL AND phone IS NULL AND email IS NULL AND display_name = 'Test Hekim'
                             FROM staff_members WHERE id = hms_test.uuid('doc_staff')),
                   'Optional values sent blank are stored as NULL (not '''' or 0)');
UPDATE staff_members SET title = 'Uzm. Dr.', phone = '0532 111 22 33' WHERE id = hms_test.uuid('doc_staff');
SELECT hms_test.ok('US-05', (SELECT display_name FROM staff_members WHERE id = hms_test.uuid('doc_staff')) = 'Uzm. Dr. Test Hekim',
                   'Administrator edits staff details; displayName follows title and name');

-- Hard deletes: administrators only, reason mandatory and logged
SELECT hms_test.expect_error('US-05', $$DELETE FROM user_accounts WHERE id = hms_test.uuid('nurse')$$,
                             'HV001', 'Deleting an account without a reason is rejected', 'delete_reason', '%deleteReason%');
SELECT hms_test.ok('US-05', user_account_delete(hms_test.uuid('nurse'), 'Görevden ayrıldı'), 'Administrator deletes an account with a reason');
SELECT hms_test.ok('US-03', (SELECT details ->> 'reason' = 'Görevden ayrıldı' AND details ->> 'username' = 'nurse.one'
                                    AND actor = hms_test.uuid('admin')
                             FROM hms_test.last_event('ACCOUNT_DELETED')),
                   'Account deletion is logged with reason, username and actor');
SELECT hms_test.ok('US-05', staff_delete(hms_test.uuid('nurse_staff'), 'Mükerrer personel kaydı'),
                   'Administrator deletes a staff member with a reason');
SELECT hms_test.ok('US-05', NOT EXISTS (SELECT 1 FROM staff_members WHERE id = hms_test.uuid('nurse_staff')),
                   'The deleted staff member is gone');
SELECT hms_test.ok('US-05', (SELECT reason = 'Mükerrer personel kaydı' AND deleted_by = hms_test.uuid('admin')
                             FROM record_deletions WHERE entity_type = 'STAFF_MEMBER' AND entity_id = hms_test.uuid('nurse_staff')),
                   'The reason text is stored in record_deletions.reason with the administrator');
SELECT hms_test.new_account('tmp', 'LabTechnician', 'Gecici', 'Personel', 'tmp.one');
SELECT auth_login_success(hms_test.uuid('tmp'), sha256('tmp-token'::bytea), now() + interval '1 hour');
SELECT hms_test.ok('US-05', staff_delete(hms_test.uuid('tmp_staff'), 'Test için açılmıştı'),
                   'Administrator deletes a staff member who has an account and a session');
SELECT hms_test.ok('US-05', NOT EXISTS (SELECT 1 FROM user_accounts WHERE id = hms_test.uuid('tmp'))
                        AND NOT EXISTS (SELECT 1 FROM auth_sessions WHERE user_account_id = hms_test.uuid('tmp')),
                   'Deleting a staff member removes the account and sessions too');
SELECT hms_test.expect_error('US-05', $$UPDATE record_deletions SET reason = 'x'$$, 'HL001', 'Deletion reasons cannot be changed');
SELECT hms_test.expect_error('US-05', $$DELETE FROM record_deletions$$, 'HL001', 'Deletion records cannot be removed');
SELECT hms_test.expect_error('US-05', $$SELECT staff_delete((SELECT staff_member_id FROM user_accounts WHERE id = hms_test.uuid('admin')), 'x')$$,
                             'HS002', 'Administrator cannot delete their own staff record');
SELECT hms_test.expect_error('US-05', $$SELECT user_account_delete(hms_test.uuid('admin'), 'x')$$,
                             'HS002', 'Administrator cannot delete their own account');
INSERT INTO role_permissions (role_id, permission_id) SELECT 2, id FROM permissions WHERE code = 'STAFF_DELETE';
UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = hms_test.uuid('admin2');
SELECT hms_test.act('rec');
SELECT hms_test.expect_error('US-05', $$SELECT user_account_delete(hms_test.uuid('admin'), 'x')$$,
                             'HS001', 'The last active administrator cannot be deleted');
SELECT hms_test.act('admin');
DELETE FROM role_permissions WHERE role_id = 2 AND permission_id = (SELECT id FROM permissions WHERE code = 'STAFF_DELETE');
UPDATE user_accounts SET status = 'ACTIVE' WHERE id = hms_test.uuid('admin2');
SELECT hms_test.act('rec');
SELECT hms_test.expect_error('US-05', $$SELECT staff_delete(hms_test.uuid('doc_staff'), 'x')$$,
                             'HA403', 'Other roles cannot delete staff');
SELECT hms_test.act('admin');

-- ============================================================================
-- C. Login, logout and sessions (US-01)
-- ============================================================================
SELECT set_config('hms_test.s1', auth_login_success(hms_test.uuid('rec'), sha256('token-1'::bytea), now() + interval '8 hours',
                                                    '10.0.0.5'::inet, 'test-agent')::text, true);
SELECT hms_test.ok('US-01', (SELECT role FROM auth_validate_session(sha256('token-1'::bytea))) = 'Receptionist',
                   'Valid login returns a session that resolves to the user role');
SELECT hms_test.ok('US-03', (SELECT actor FROM hms_test.last_event('LOGIN_SUCCESS')) = hms_test.uuid('rec'), 'Login is logged');
SELECT hms_test.ok('US-01', auth_logout(hms_test.uuid('s1')), 'Logout ends the session');
SELECT hms_test.ok('US-01', NOT EXISTS (SELECT 1 FROM auth_validate_session(sha256('token-1'::bytea))),
                   'After logout the token is no longer valid');
SELECT hms_test.ok('US-01', NOT auth_logout(hms_test.uuid('s1')), 'Second logout reports that the session already ended');
SELECT hms_test.expect_error('US-01', $$UPDATE auth_sessions SET revoke_reason = 'ADMIN_REVOKED' WHERE id = hms_test.uuid('s1')$$,
                             'HS007', 'An ended session cannot be changed');
SELECT hms_test.expect_error('US-01', $$INSERT INTO auth_sessions (user_account_id, token_hash, expires_at)
                                        VALUES (hms_test.uuid('rec'), sha256('x'::bytea), now() + interval '1 hour')$$,
                             'HA403', 'Sessions can only be created by auth_login_success()');

SELECT auth_login_failure('REC.one', '10.0.0.9'::inet);
SELECT hms_test.ok('US-01', (SELECT failed_login_count FROM user_accounts WHERE id = hms_test.uuid('rec')) = 1,
                   'Wrong password increments the failed-login counter');
SELECT hms_test.ok('US-03', (SELECT details ->> 'reason' FROM hms_test.last_event('LOGIN_FAILURE')) = 'BAD_CREDENTIALS'
                        AND (SELECT details ? 'password' FROM hms_test.last_event('LOGIN_FAILURE')) IS FALSE,
                   'Failed login is logged without any password');
SELECT auth_login_failure('nobody.here');
SELECT hms_test.ok('US-03', (SELECT entity IS NULL AND details ->> 'username' = 'nobody.here' FROM hms_test.last_event('LOGIN_FAILURE')),
                   'Failed login for an unknown username is logged');
SELECT set_config('hms_test.s2', auth_login_success(hms_test.uuid('rec'), sha256('token-2'::bytea), now() + interval '8 hours')::text, true);
SELECT hms_test.ok('US-01', (SELECT failed_login_count FROM user_accounts WHERE id = hms_test.uuid('rec')) = 0,
                   'Successful login resets the failed-login counter');
SELECT hms_test.ok('US-06', (SELECT user_agent IS NULL AND client_ip IS NULL FROM auth_sessions WHERE id = hms_test.uuid('s2')),
                   'Session details that were not provided are NULL');

SELECT set_config('hms_test.s9', auth_login_success(hms_test.uuid('rec'), sha256('token-9'::bytea), now() + interval '8 hours')::text, true);
SELECT hms_test.act('rec');
SELECT hms_test.expect_error('US-05', $$SELECT auth_revoke_session(hms_test.uuid('s9'))$$, 'HA403', 'Other roles cannot end someone''s session');
SELECT hms_test.expect_error('US-05', $$DELETE FROM auth_sessions WHERE id = hms_test.uuid('s9')$$, 'HA403', 'Other roles cannot delete sessions');
SELECT hms_test.act('admin');
SELECT hms_test.ok('US-05', auth_revoke_session(hms_test.uuid('s9')), 'Administrator ends any session');
SELECT hms_test.ok('US-05', (SELECT revoke_reason FROM auth_sessions WHERE id = hms_test.uuid('s9')) = 'ADMIN_REVOKED'
                        AND (SELECT actor FROM hms_test.last_event('SESSION_REVOKED')) = hms_test.uuid('admin'),
                   'Ending a session by an administrator is logged');
DELETE FROM auth_sessions WHERE id = hms_test.uuid('s9');
SELECT hms_test.ok('US-05', NOT EXISTS (SELECT 1 FROM auth_sessions WHERE id = hms_test.uuid('s9'))
                        AND NOT (SELECT details::text LIKE '%token%' FROM hms_test.last_event('RECORD_DELETE')),
                   'Administrator deletes a session (logged without the token)');

SELECT hms_test.act('admin');
UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = hms_test.uuid('rec');
SELECT hms_test.ok('US-05', (SELECT revoke_reason FROM auth_sessions WHERE id = hms_test.uuid('s2')) = 'ACCOUNT_DEACTIVATED',
                   'Deactivation ends the user''s open sessions');
SELECT hms_test.ok('US-05', auth_login_success(hms_test.uuid('rec'), sha256('token-3'::bytea), now() + interval '8 hours') IS NULL,
                   'A deactivated user cannot log in');
SELECT hms_test.ok('US-03', (SELECT result = 3 AND details ->> 'reason' = 'ACCOUNT_DEACTIVATED' FROM hms_test.last_event('LOGIN_FAILURE')),
                   'Blocked login of a deactivated user is logged as DENIED');
UPDATE user_accounts SET status = 'ACTIVE' WHERE id = hms_test.uuid('rec');

SELECT set_config('hms_test.s3', auth_login_success(hms_test.uuid('rec'), sha256('token-4'::bytea), now() + interval '8 hours')::text, true);
UPDATE user_accounts SET password_hash = hms_test.hash('d'), must_change_password = true WHERE id = hms_test.uuid('rec');
SELECT hms_test.ok('US-05', (SELECT revoke_reason FROM auth_sessions WHERE id = hms_test.uuid('s3')) = 'PASSWORD_CHANGED'
                        AND (SELECT actor FROM hms_test.last_event('ACCOUNT_PASSWORD_RESET')) = hms_test.uuid('admin'),
                   'Administrator password reset ends sessions and is logged');
SELECT hms_test.ok('US-03', NOT EXISTS (SELECT 1 FROM audit_log WHERE details::text LIKE '%$2a$10$%'),
                   'Password hashes never appear in the audit log');

SELECT set_config('hms_test.s4', auth_login_success(hms_test.uuid('rec'), sha256('token-5'::bytea), now() + interval '8 hours')::text, true);
SELECT set_config('hms_test.s5', auth_login_success(hms_test.uuid('rec'), sha256('token-6'::bytea), now() + interval '8 hours')::text, true);
SELECT hms_test.act('rec', 's4');
SELECT auth_change_own_password(hms_test.hash('e'));
SELECT hms_test.ok('US-01', (SELECT revoked_at IS NULL FROM auth_sessions WHERE id = hms_test.uuid('s4'))
                        AND (SELECT revoke_reason FROM auth_sessions WHERE id = hms_test.uuid('s5')) = 'PASSWORD_CHANGED'
                        AND NOT (SELECT must_change_password FROM user_accounts WHERE id = hms_test.uuid('rec')),
                   'Own password change keeps the current session and ends the others');
SELECT hms_test.ok('US-03', (SELECT actor FROM hms_test.last_event('ACCOUNT_PASSWORD_CHANGED')) = hms_test.uuid('rec'),
                   'Own password change is logged');
SELECT hms_test.expect_error('US-05', $$UPDATE user_accounts SET status = 'DEACTIVATED' WHERE id = hms_test.uuid('doc')$$,
                             'HA403', 'A receptionist cannot deactivate other accounts');

-- ============================================================================
-- D. Patient registration (US-06)
-- ============================================================================
SELECT hms_test.act('rec', 's4');

DO $$
DECLARE
    v_column text;
    v_type   text;
    v_detail text;
    v_state  text;
BEGIN
    BEGIN
        INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
        VALUES ('', '', 'Yılmaz', NULL, ' ', '', '', '');
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE, v_column = COLUMN_NAME,
                                v_type = PG_DATATYPE_NAME, v_detail = PG_EXCEPTION_DETAIL;
    END;
    PERFORM hms_test.ok('US-06', v_state = 'HV001' AND v_column = 'national_id' AND v_type = 'text',
                        'Blank fields raise HV001 with COLUMN and DATATYPE of the first blank field',
                        format('%s %s %s', v_state, v_column, v_type));
    PERFORM hms_test.ok('US-06', jsonb_array_length(v_detail::jsonb) = 7
                        AND (SELECT bool_and(e ->> 'error' = 'REQUIRED') FROM jsonb_array_elements(v_detail::jsonb) e),
                        'DETAIL lists every blank field at once (7 of 8; last name was filled)', v_detail);
    PERFORM hms_test.ok('US-06', (SELECT e ->> 'dataType' FROM jsonb_array_elements(v_detail::jsonb) e
                                  WHERE e ->> 'column' = 'date_of_birth') = 'date'
                        AND (SELECT e ->> 'field' FROM jsonb_array_elements(v_detail::jsonb) e
                             WHERE e ->> 'column' = 'date_of_birth') = 'dateOfBirth',
                        'Each error carries column, API field name and data type (date_of_birth -> date)');
    PERFORM hms_test.ok('US-06', (SELECT e ->> 'message' FROM jsonb_array_elements(v_detail::jsonb) e
                                  WHERE e ->> 'column' = 'first_name') = 'Ad alanı zorunludur ve boş bırakılamaz.',
                        'Each error carries a ready Turkish message');
END
$$;

SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', 'Ayşe', 'Test', NULL, 'Kadın', '0532 000 00 00', 'Adres', 'MILD')$$,
    'HV001', 'Missing date of birth is reported as column date_of_birth', 'date_of_birth', '%"dataType": "date"%');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('123', 'Ayşe', 'Test', '1990-05-01', 'Kadın', '0532 000 00 00', 'Adres', 'MILD')$$,
    'HV001', 'National ID must be 11 digits', 'national_id', '%INVALID_FORMAT%');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', 'Ayşe', 'Test', current_date + 1, 'Kadın', '0532 000 00 00', 'Adres', 'MILD')$$,
    'HV001', 'Date of birth cannot be in the future', 'date_of_birth', '%OUT_OF_RANGE%');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', 'Ayşe', 'Test', '1990-05-01', 'Kadın', '0532 000 00 00', 'Adres', 'SEVERE')$$,
    'HV001', 'Condition level must be MILD, MODERATE or ADVANCED', 'condition_level', '%NOT_ALLOWED%');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', 'Ayşe', 'Test', '1990-05-01', 'Kadın', 'telefon yok', 'Adres', 'MILD')$$,
    'HV001', 'Invalid phone is rejected', 'phone', '%INVALID_FORMAT%');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', repeat('a', 101), 'Test', '1990-05-01', 'Kadın', '0532 000 00 00', 'Adres', 'MILD')$$,
    'HV001', 'First name longer than 100 characters is rejected', 'first_name', '%TOO_LONG%');

INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
VALUES ('10000000146', 'Ayşe', 'Test', '1990-05-01', 'Kadın', '0532 000 00 00', 'Test Mah. 1', 'MILD')
RETURNING set_config('hms_test.p1', id::text, true);
SELECT hms_test.ok('US-06', EXISTS (SELECT 1 FROM patients WHERE id = hms_test.uuid('p1')), 'Valid MILD patient without relative is registered');
SELECT hms_test.ok('US-03', (SELECT actor = hms_test.uuid('rec') AND details ? 'fields' AND NOT details ? 'national_id'
                             FROM hms_test.last_event('RECORD_CREATE')),
                   'Registration is logged with actor; values are not duplicated into the log');
SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('10000000146', 'Fatma', 'Test', '1980-01-01', 'Kadın', '0532 000 00 01', 'Adres', 'MILD')$$,
    'HV001', 'A second patient with the same national ID is rejected', 'national_id', '%DUPLICATE%');

SELECT hms_test.expect_error('US-06', $$INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('20000000146', 'Ali', 'Test', '1950-01-01', 'Erkek', '0532 000 00 02', 'Adres', 'ADVANCED')$$,
    'HV001', 'ADVANCED patient without relative cannot be saved', NULL, '%RELATIVE_REQUIRED%');

DO $$
DECLARE
    v_patient uuid;
BEGIN
    INSERT INTO patients (national_id, first_name, last_name, date_of_birth, gender, phone, address, condition_level)
    VALUES ('20000000146', 'Ali', 'Test', '1950-01-01', 'Erkek', '0532 000 00 02', 'Adres', 'ADVANCED')
    RETURNING id INTO v_patient;
    INSERT INTO patient_relatives (patient_id, full_name, relationship, phone)
    VALUES (v_patient, 'Veli Test', 'Oğlu', '0532 000 00 03');
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM set_config('hms_test.p2', v_patient::text, true);
    PERFORM hms_test.ok('US-06', true, 'ADVANCED patient with relative information is registered');
END
$$;

SELECT hms_test.expect_error('US-06', $$INSERT INTO patient_relatives (patient_id, full_name, relationship, phone)
    VALUES (hms_test.uuid('p1'), '', 'Annesi', '')$$,
    'HV001', 'Blank relative fields are rejected with their columns', 'full_name', '%relativeInformation.phone%');
SELECT hms_test.expect_error('US-06', $$UPDATE patients SET condition_level = 'ADVANCED' WHERE id = hms_test.uuid('p1')$$,
    'HV001', 'Raising a patient to ADVANCED without a relative cannot be saved', NULL, '%RELATIVE_REQUIRED%');
SELECT hms_test.expect_error('US-06', $$DELETE FROM patient_relatives WHERE patient_id = hms_test.uuid('p2')$$,
    'HV001', 'The relative of an ADVANCED patient cannot be removed', NULL, '%RELATIVE_REQUIRED%');
SELECT hms_test.ok('US-06', jsonb_array_length(hms_field_errors('patients',
           '{"national_id":"30000000146","first_name":"","last_name":"Test","date_of_birth":"2000-01-01",
             "gender":"Kadın","phone":"05320000004","address":"Adres","condition_level":"MILD"}'::jsonb)) = 1,
       'hms_field_errors() pre-validates a payload without writing');
SELECT hms_test.expect_error('US-06', $$SELECT patient_delete(hms_test.uuid('p1'), 'Yanlış kayıt')$$,
    'HA403', 'A receptionist cannot delete patients');

-- ============================================================================
-- E. Patient updates (US-08)
-- ============================================================================
UPDATE patients SET phone = '0533 999 88 77', address = 'Yeni Mah. 2' WHERE id = hms_test.uuid('p1');
SELECT hms_test.ok('US-08', (SELECT details -> 'changes' -> 'phone' FROM hms_test.last_event('RECORD_UPDATE'))
                            = '["0532 000 00 00", "0533 999 88 77"]'::jsonb
                        AND (SELECT details -> 'changes' ? 'address' FROM hms_test.last_event('RECORD_UPDATE'))
                        AND (SELECT actor FROM hms_test.last_event('RECORD_UPDATE')) = hms_test.uuid('rec'),
                   'Receptionist update is logged with before and after values');
SELECT hms_test.expect_error('US-08', $$UPDATE patients SET national_id = '99999999990' WHERE id = hms_test.uuid('p1')$$,
    'HV001', 'National ID cannot be updated', 'national_id', '%IMMUTABLE%');
SELECT hms_test.expect_error('US-08', $$UPDATE patients SET address = '' WHERE id = hms_test.uuid('p1')$$,
    'HV001', 'US-06 validation applies to updates', 'address', '%REQUIRED%');
UPDATE patient_relatives SET phone = '0532 000 00 09' WHERE patient_id = hms_test.uuid('p2');
SELECT hms_test.ok('US-08', (SELECT entity = hms_test.uuid('p2') AND details -> 'changes' ? 'phone'
                             FROM hms_test.last_event('RECORD_UPDATE')),
                   'Relative information updates are logged under the patient id');
SELECT hms_test.act('doc');
SELECT hms_test.expect_error('US-08', $$UPDATE patients SET phone = '0533 000 00 00' WHERE id = hms_test.uuid('p1')$$,
    'HA403', 'A physician (no PATIENT_UPDATE) cannot update patient information');

-- Administrator: corrects the national ID and deletes a patient completely
SELECT hms_test.act('admin');
UPDATE patients SET national_id = '10000000154' WHERE id = hms_test.uuid('p1');
SELECT hms_test.ok('US-08', (SELECT details -> 'changes' -> 'national_id' FROM hms_test.last_event('RECORD_UPDATE'))
                            = '["10000000146", "10000000154"]'::jsonb,
                   'Administrator can correct a national ID (logged with before/after)');
SELECT hms_test.expect_error('US-06', $$DELETE FROM patients WHERE id = hms_test.uuid('p2')$$,
    'HV001', 'Deleting a patient without a reason is rejected', 'delete_reason', '%REQUIRED%');
SELECT hms_test.expect_error('US-06', $$SELECT patient_delete(hms_test.uuid('p2'), '   ')$$,
    'HV001', 'A blank deletion reason is rejected', 'delete_reason', '%deleteReason%');
SELECT hms_test.ok('US-06', patient_delete(hms_test.uuid('p2'), 'Hasta kaydı yanlışlıkla iki kez açılmış'),
                   'Administrator deletes an ADVANCED patient with relative at any time');
SELECT hms_test.ok('US-06', NOT EXISTS (SELECT 1 FROM patients WHERE id = hms_test.uuid('p2'))
                        AND NOT EXISTS (SELECT 1 FROM patient_relatives WHERE patient_id = hms_test.uuid('p2')),
                   'Complete deletion removes the patient and the relative information');
SELECT hms_test.ok('US-06', (SELECT reason = 'Hasta kaydı yanlışlıkla iki kez açılmış' AND deleted_by = hms_test.uuid('admin')
                             FROM record_deletions WHERE entity_type = 'PATIENT' AND entity_id = hms_test.uuid('p2')),
                   'The patient deletion reason is stored as text in record_deletions.reason');
SELECT hms_test.ok('US-03', (SELECT count(*) FROM audit_log l JOIN audit_actions a ON a.id = l.action_id
                             WHERE a.code = 'RECORD_DELETE' AND l.entity_id = hms_test.uuid('p2')
                               AND l.actor_user_id = hms_test.uuid('admin')
                               AND l.details ->> 'reason' = 'Hasta kaydı yanlışlıkla iki kez açılmış') = 2,
                   'Both deletions (patient, relative) are logged with the reason and the administrator');
SELECT hms_test.ok('US-03', NOT EXISTS (SELECT 1 FROM audit_log l JOIN audit_actions a ON a.id = l.action_id
                                        WHERE a.code = 'RECORD_DELETE' AND l.entity_id = hms_test.uuid('p2')
                                          AND (l.details::text LIKE '%20000000146%' OR l.details::text LIKE '%Veli Test%')),
                   'The deleted values are not copied into the log');
SELECT hms_test.ok('US-06', NOT patient_delete(hms_test.uuid('p2'), 'Tekrar'), 'Deleting a patient that no longer exists returns false');

-- ============================================================================
-- F. Audit log immutability and access (US-03, US-04)
-- ============================================================================
SELECT hms_test.act('rec', 's4');
SELECT audit_record('UI_CLICK', NULL, NULL,
                    '{"element":"login-button","route":"/login","password":"x","nested":{"accessToken":"y"}}'::jsonb);
SELECT hms_test.ok('US-03', (SELECT details ->> 'password' = '[REDACTED]' AND details -> 'nested' ->> 'accessToken' = '[REDACTED]'
                                    AND details ->> 'element' = 'login-button' AND actor = hms_test.uuid('rec')
                             FROM hms_test.last_event('UI_CLICK')),
                   'UI click is logged with actor; secrets are redacted');
SELECT hms_test.ok('US-03', audit_record_batch('[{"action":"UI_NAVIGATION","details":{"route":"/patients"}},
                                                {"action":"UI_CLICK","details":{"element":"search"}},
                                                {"action":"UI_INPUT","details":{"field":"lastName"}}]'::jsonb) = 3,
                   'A batch of UI events is logged in one call');
SELECT hms_test.ok('US-03', (SELECT session_id FROM audit_log ORDER BY id DESC LIMIT 1) = hms_test.uuid('s4'),
                   'Events carry the session (IP and user agent are stored once per session)');
SELECT hms_test.expect_error('US-03', $$SELECT audit_record('NOT_AN_ACTION')$$, 'HL002', 'Unknown action codes are rejected');
SELECT hms_test.expect_error('US-03', $$SELECT audit_record('UI_OTHER', NULL, NULL, jsonb_build_object('blob', repeat('x', 9000)))$$,
                             'HL003', 'Oversized details are rejected (keeps the log small)');

SELECT hms_test.expect_error('US-03', $$UPDATE audit_log SET result = 2$$, 'HL001', 'Audit rows cannot be updated');
SELECT hms_test.expect_error('US-03', $$DELETE FROM audit_log$$, 'HL001', 'Audit rows cannot be deleted');
SELECT hms_test.expect_error('US-03', $$TRUNCATE audit_log$$, 'HL001', 'Audit log cannot be truncated');
SELECT hms_test.expect_error('US-03', format('TRUNCATE %I', audit_partition_name(date_trunc('month', now() AT TIME ZONE 'UTC')::date)),
                             'HL001', 'A monthly partition cannot be truncated');
SELECT hms_test.expect_error('US-03', $$INSERT INTO audit_log (action_id, result) VALUES (10, 1)$$,
                             'HL001', 'Rows cannot be inserted directly (no back-dating or forged actors)');
SELECT hms_test.expect_error('US-03', format('DROP TABLE %I', audit_partition_name(date_trunc('month', now() AT TIME ZONE 'UTC')::date)),
                             'HL001', 'A partition that is not archived cannot be dropped');
SELECT hms_test.expect_error('US-03', $$ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_immutable$$,
                             'HL001', 'Audit triggers cannot be disabled');
SELECT hms_test.expect_error('US-03', $$DROP TABLE audit_log CASCADE$$, 'HL001', 'The audit log cannot be dropped (not even with CASCADE)');
SELECT hms_test.expect_error('US-03', $$DROP TABLE record_deletions$$, 'HL001', 'The deletion register cannot be dropped');
SELECT hms_test.expect_error('US-03', $$UPDATE audit_archives SET file_name = 'x'$$, 'HL001', 'Archive registry is append-only');
SELECT hms_test.expect_error('US-03', $$DELETE FROM audit_archives$$, 'HL001', 'Archive registry rows cannot be deleted');
SELECT hms_test.expect_error('US-03', $$UPDATE audit_log_restored SET action = 'X'$$, 'HL001', 'Restored archive rows cannot be changed');
SELECT hms_test.expect_error('US-03', $$UPDATE audit_log SET result = 2 WHERE false$$, 'HL001', 'Any UPDATE statement on the log fails, even with no matching row');

DO $$
DECLARE
    v_state text;
BEGIN
    SET LOCAL ROLE hms_app;
    BEGIN
        PERFORM 1 FROM audit_log LIMIT 1;
        v_state := '00000';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    END;
    PERFORM hms_test.ok('US-04', v_state = '42501', 'Backend role cannot read audit_log directly', v_state);
    BEGIN
        PERFORM audit_write(NULL, 'UI_CLICK', NULL, NULL, NULL, 'SUCCESS', NULL);
        v_state := '00000';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    END;
    PERFORM hms_test.ok('US-03', v_state = '42501', 'Backend role cannot call the internal writer', v_state);
    BEGIN
        PERFORM audit_record('UI_CLICK', NULL, NULL, '{"element":"x"}'::jsonb);
        v_state := '00000';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    END;
    PERFORM hms_test.ok('US-03', v_state = '00000', 'Backend role records events through audit_record()', v_state);
    BEGIN
        PERFORM patient_delete(hms_test.uuid('p1'), 'Deneme');
        v_state := '00000';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
    END;
    PERFORM hms_test.ok('US-06', v_state = 'HA403', 'Backend role deletes patients only for administrators (receptionist: HA403)', v_state);
    RESET ROLE;
END
$$;

SELECT hms_test.expect_error('US-04', $$SELECT * FROM audit_search()$$, 'HA403', 'Only administrators can read the audit log');
SELECT hms_test.act('admin');
SELECT hms_test.ok('US-04', (SELECT count(*) FROM audit_search(p_action => 'RECORD_UPDATE', p_entity_type => 'PATIENT')) >= 1
                        AND (SELECT bool_and(action = 'RECORD_UPDATE') FROM audit_search(p_action => 'RECORD_UPDATE')),
                   'Administrator filters the log by action type');
SELECT hms_test.ok('US-04', (SELECT bool_and(actor_user_id = hms_test.uuid('rec') AND actor_username = 'rec.one')
                             FROM audit_search(p_actor_user_id => hms_test.uuid('rec'), p_limit => 1000)),
                   'Administrator filters the log by user (username and role resolved)');
SELECT hms_test.ok('US-04', (SELECT count(*) FROM audit_search(p_from => now() + interval '1 day')) = 0
                        AND (SELECT count(*) FROM audit_search(p_from => now() - interval '1 day', p_to => now() + interval '1 day')) > 0,
                   'Administrator filters the log by date range');
SELECT hms_test.ok('US-04', (SELECT actor FROM hms_test.last_event('AUDIT_LOG_VIEWED')) = hms_test.uuid('admin'),
                   'Reading the log is itself logged');
SELECT hms_test.ok('US-03', NOT EXISTS (SELECT 1 FROM audit_log l JOIN audit_actions a ON a.id = l.action_id
                                        WHERE a.code IN ('RECORD_CREATE', 'RECORD_UPDATE', 'RECORD_DELETE')
                                          AND l.entity_type_id IN (1, 2) AND l.actor_user_id IS NULL),
                   'Every patient data change has an actor');
SELECT hms_test.ok('US-03', (SELECT bool_and(l.actor_role_id = s.role_id) FROM audit_log l
                             JOIN user_accounts a ON a.id = l.actor_user_id JOIN staff_members s ON s.id = a.staff_member_id
                             WHERE l.actor_user_id = hms_test.uuid('rec')),
                   'Events store the actor role at the time of the action');

-- ============================================================================
-- Results
-- ============================================================================
\pset tuples_only off
\echo
SELECT n, story, CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END AS status, label,
       CASE WHEN passed THEN '' ELSE info END AS info
FROM hms_test.results ORDER BY n;

SELECT count(*) FILTER (WHERE passed) AS passed, count(*) FILTER (WHERE NOT passed) AS failed
FROM hms_test.results \gset
\echo Passed: :passed  Failed: :failed

ROLLBACK;

SELECT :failed = 0 AS all_tests_passed \gset
\if :all_tests_passed
    \echo ALL TESTS PASSED
\else
    \echo SOME TESTS FAILED
    DO $$ BEGIN RAISE EXCEPTION 'schema tests failed'; END $$;
\endif
