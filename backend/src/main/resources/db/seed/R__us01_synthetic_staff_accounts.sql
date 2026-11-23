-- LOCAL DEMO ONLY.
-- Loaded only when classpath:db/seed is enabled.
-- Existing accounts and passwords are never overwritten.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

SELECT set_config('hms.trusted_op', 'migration', true);

CREATE TEMPORARY TABLE seed_demo_accounts (
    staff_id uuid PRIMARY KEY,
    username text NOT NULL,
    role_code text NOT NULL,
    first_name text NOT NULL,
    last_name text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_demo_accounts VALUES
('5e0d0000-0000-4000-8000-000000000301', 'reception.demo', 'Receptionist', 'Demo', 'Reception'),
('5e0d0000-0000-4000-8000-000000000302', 'doctor.demo', 'Physician', 'Demo', 'Physician'),
('5e0d0000-0000-4000-8000-000000000303', 'nurse.demo', 'Nurse', 'Demo', 'Nurse'),
('5e0d0000-0000-4000-8000-000000000304', 'care.demo', 'PatientCareAssistant', 'Demo', 'Care'),
('5e0d0000-0000-4000-8000-000000000305', 'lab.demo', 'LabTechnician', 'Demo', 'Laboratory'),
('5e0d0000-0000-4000-8000-000000000306', 'cleaning.demo', 'CleaningStaff', 'Demo', 'Cleaning');

-- Insert missing staff without changing existing records.

INSERT INTO staff_members (
    id, role_id, first_name, last_name
)
SELECT d.staff_id, r.id, d.first_name, d.last_name
FROM seed_demo_accounts d
JOIN roles r ON r.code = d.role_code
WHERE NOT EXISTS (
    SELECT 1 FROM user_accounts a
    WHERE lower(a.username) = lower(d.username)
)
ON CONFLICT (id) DO NOTHING;

-- Store passwords as BCrypt hashes.

INSERT INTO user_accounts (
    staff_member_id,
    username,
    password_hash,
    must_change_password
)
SELECT
    d.staff_id,
    d.username,
    '{bcrypt}' || crypt('DemoOnly!321', gen_salt('bf', 12)),
    false
FROM seed_demo_accounts d
JOIN staff_members s ON s.id = d.staff_id
JOIN roles r ON r.id = s.role_id
            AND r.code = d.role_code
WHERE NOT EXISTS (
    SELECT 1 FROM user_accounts a
    WHERE a.staff_member_id = d.staff_id
       OR lower(a.username) = lower(d.username)
)
ON CONFLICT DO NOTHING;

SELECT set_config('hms.trusted_op', '', true);