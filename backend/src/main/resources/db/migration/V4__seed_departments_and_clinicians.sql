-- =============================================================================
-- HMS Community | Flyway migration V4
-- Reference seed data: clinical departments and physicians (clinicians).
--
-- Replaces the deprecated Docker init_and_seed.sql CSV COPY approach.
-- Maps CSV records into the V1 normalized schema:
--   * departments        (id, name, is_active)
--   * staff_members      (id, role_id = Physician, first_name, last_name, title)
--   * doctor_departments (staff_member_id, department_id)
--   * doctors (view)     automatically exposes the clinician directory (US-09)
-- =============================================================================

SELECT set_config('hms.trusted_op', 'migration', false);

-- -----------------------------------------------------------------------------
-- 1. Departments
-- -----------------------------------------------------------------------------
INSERT INTO departments (id, name, is_active) VALUES
    ('9fecd79a-9ed6-59c8-9345-813bc15a6e13'::uuid, 'Adli Tıp', true),
    ('1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid, 'Kardiyoloji', true),
    ('3e012ef6-0833-505e-8b43-3c618893df45'::uuid, 'İç Hastalıkları', true),
    ('6cb6c0a4-e92c-5023-98fe-8268525babb1'::uuid, 'Nöroloji', true),
    ('e21b722a-86c9-5ad2-bad7-f503df5e6a33'::uuid, 'Genel Cerrahi', true),
    ('7690524a-267f-5774-b128-8c4405a09902'::uuid, 'Ortopedi ve Travmatoloji', true),
    ('5495f47f-b0e0-54e4-b7fa-187cf797501b'::uuid, 'Göz Hastalıkları', true),
    ('3bc1edfc-1f3f-54d7-aeb1-6b65e0cd6fda'::uuid, 'Tıbbi Biyokimya', true)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Clinicians (staff_members with role = Physician)
--    Note: display_name is a STORED generated column in staff_members.
-- -----------------------------------------------------------------------------
WITH physician_role AS (
    SELECT id FROM roles WHERE code = 'Physician'
)
INSERT INTO staff_members (id, role_id, first_name, last_name, title)
SELECT v.id, r.id, v.first_name, v.last_name, v.title
FROM physician_role r
CROSS JOIN (VALUES
    ('5f595750-b5aa-cbd5-123a-22bb1d220247'::uuid, 'Habib', 'YAZAR', 'Uzm. Prof.'),
    ('0f7987a0-b28a-5b35-953b-8caa4d250d57'::uuid, 'Ahmet', 'ÇELİK', 'Dr. Öğr. Üyesi'),
    ('5e9932f6-df68-53b3-9309-af1f46930de0'::uuid, 'Mehmet', 'YILDIRIM', 'Uzm. Dr.'),
    ('21e2a9c9-04a7-51cd-bb50-8652e2278c37'::uuid, 'Ayşe', 'ÖZDEMİR', 'Doç. Dr.'),
    ('68286354-41a7-5859-aed3-b16b9fef9007'::uuid, 'Fatma', 'KILIÇ', 'Dr. Öğr. Üyesi'),
    ('a9576c94-c145-5754-8148-d2fc14082bcf'::uuid, 'Mustafa', 'KARA', 'Uzm. Dr.'),
    ('4c3c0a7a-33e0-5470-a3ab-78e628664a24'::uuid, 'Zeynep', 'ÖZKAN', 'Prof. Dr.'),
    ('f811b902-5852-589e-a6b5-d6af39ee353b'::uuid, 'Emre', 'KORKMAZ', 'Dr. Öğr. Üyesi'),
    ('334f9019-86db-572f-a567-cff2878d8a52'::uuid, 'Elif', 'GÜNEŞ', 'Uzm. Dr.'),
    ('99d8cd86-57ef-5aee-8390-52dac516a014'::uuid, 'Burak', 'DEMİR', 'Prof. Dr.'),
    ('c86d494e-f610-50ad-96b2-b9894b623b6e'::uuid, 'Selin', 'YILDIZ', 'Doç. Dr.'),
    ('ee0503a6-0192-5f9f-b2b0-8da3cb7f603f'::uuid, 'Murat', 'AYDIN', 'Uzm. Dr.'),
    ('ee27f59d-105b-57e4-8133-c963049ec6e0'::uuid, 'Esra', 'DOĞAN', 'Prof. Dr.'),
    ('7853c35d-39c0-588c-ac0c-cb16f0ddebbd'::uuid, 'Can', 'ÇETİN', 'Doç. Dr.'),
    ('69414ed1-6ece-5187-aea4-5e8cfd0e5d8d'::uuid, 'Merve', 'KURT', 'Op. Dr.'),
    ('364ccaab-6469-5af9-b6c8-3ea7dc6910f5'::uuid, 'Hakan', 'POLAT', 'Prof. Dr.'),
    ('37b88ae7-5155-5c3e-aade-b9849154a2c5'::uuid, 'Özge', 'ERDOĞAN', 'Doç. Dr.'),
    ('05256150-bb4a-52a0-b6b6-1ac10a1a94b0'::uuid, 'Serkan', 'KAYA', 'Dr. Öğr. Üyesi'),
    ('cecd75e8-be1c-5085-a5bf-9891502ebe7b'::uuid, 'Gamze', 'ŞAHİN', 'Prof. Dr.'),
    ('336999af-9489-5d87-8b75-6448135c1a5f'::uuid, 'Kemal', 'ÖZTÜRK', 'Doç. Dr.'),
    ('cf3924a5-1858-5268-9242-b0a817e74a86'::uuid, 'Büşra', 'ARSLAN', 'Dr. Öğr. Üyesi'),
    ('8bb3ee99-e299-5d5a-92c0-b29ff79426fe'::uuid, 'Tolga', 'ASLAN', 'Prof. Dr.'),
    ('c66149dd-ea43-56da-9c55-1eafba121686'::uuid, 'Derya', 'KOÇ', 'Doç. Dr.'),
    ('ba23c040-4459-5f48-aa57-73070481dfee'::uuid, 'Onur', 'ŞİMŞEK', 'Dr. Öğr. Üyesi'),
    ('718f474f-563c-5166-9ae0-ae86104e38c0'::uuid, 'İrem', 'ÇAKIR', 'Prof. Dr.'),
    ('57b4000a-ec3d-535c-9a7d-7ab8b397e066'::uuid, 'Gökhan', 'YILMAZ', 'Doç. Dr.')
) AS v(id, first_name, last_name, title)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. Physician-to-Department assignments (doctor_departments)
-- -----------------------------------------------------------------------------
INSERT INTO doctor_departments (staff_member_id, department_id) VALUES
    ('5f595750-b5aa-cbd5-123a-22bb1d220247'::uuid, '1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid),
    ('0f7987a0-b28a-5b35-953b-8caa4d250d57'::uuid, '9fecd79a-9ed6-59c8-9345-813bc15a6e13'::uuid),
    ('5e9932f6-df68-53b3-9309-af1f46930de0'::uuid, '9fecd79a-9ed6-59c8-9345-813bc15a6e13'::uuid),
    ('21e2a9c9-04a7-51cd-bb50-8652e2278c37'::uuid, '1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid),
    ('68286354-41a7-5859-aed3-b16b9fef9007'::uuid, '1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid),
    ('a9576c94-c145-5754-8148-d2fc14082bcf'::uuid, '1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid),
    ('4c3c0a7a-33e0-5470-a3ab-78e628664a24'::uuid, '1e790629-a7b2-5af4-97fa-c8e678fd92cf'::uuid),
    ('f811b902-5852-589e-a6b5-d6af39ee353b'::uuid, '3e012ef6-0833-505e-8b43-3c618893df45'::uuid),
    ('334f9019-86db-572f-a567-cff2878d8a52'::uuid, '3e012ef6-0833-505e-8b43-3c618893df45'::uuid),
    ('99d8cd86-57ef-5aee-8390-52dac516a014'::uuid, '3e012ef6-0833-505e-8b43-3c618893df45'::uuid),
    ('c86d494e-f610-50ad-96b2-b9894b623b6e'::uuid, '3e012ef6-0833-505e-8b43-3c618893df45'::uuid),
    ('ee0503a6-0192-5f9f-b2b0-8da3cb7f603f'::uuid, '6cb6c0a4-e92c-5023-98fe-8268525babb1'::uuid),
    ('ee27f59d-105b-57e4-8133-c963049ec6e0'::uuid, '6cb6c0a4-e92c-5023-98fe-8268525babb1'::uuid),
    ('7853c35d-39c0-588c-ac0c-cb16f0ddebbd'::uuid, '6cb6c0a4-e92c-5023-98fe-8268525babb1'::uuid),
    ('69414ed1-6ece-5187-aea4-5e8cfd0e5d8d'::uuid, 'e21b722a-86c9-5ad2-bad7-f503df5e6a33'::uuid),
    ('364ccaab-6469-5af9-b6c8-3ea7dc6910f5'::uuid, 'e21b722a-86c9-5ad2-bad7-f503df5e6a33'::uuid),
    ('37b88ae7-5155-5c3e-aade-b9849154a2c5'::uuid, 'e21b722a-86c9-5ad2-bad7-f503df5e6a33'::uuid),
    ('05256150-bb4a-52a0-b6b6-1ac10a1a94b0'::uuid, 'e21b722a-86c9-5ad2-bad7-f503df5e6a33'::uuid),
    ('cecd75e8-be1c-5085-a5bf-9891502ebe7b'::uuid, '7690524a-267f-5774-b128-8c4405a09902'::uuid),
    ('336999af-9489-5d87-8b75-6448135c1a5f'::uuid, '7690524a-267f-5774-b128-8c4405a09902'::uuid),
    ('cf3924a5-1858-5268-9242-b0a817e74a86'::uuid, '7690524a-267f-5774-b128-8c4405a09902'::uuid),
    ('8bb3ee99-e299-5d5a-92c0-b29ff79426fe'::uuid, '5495f47f-b0e0-54e4-b7fa-187cf797501b'::uuid),
    ('c66149dd-ea43-56da-9c55-1eafba121686'::uuid, '5495f47f-b0e0-54e4-b7fa-187cf797501b'::uuid),
    ('ba23c040-4459-5f48-aa57-73070481dfee'::uuid, '5495f47f-b0e0-54e4-b7fa-187cf797501b'::uuid),
    ('718f474f-563c-5166-9ae0-ae86104e38c0'::uuid, '3bc1edfc-1f3f-54d7-aeb1-6b65e0cd6fda'::uuid),
    ('57b4000a-ec3d-535c-9a7d-7ab8b397e066'::uuid, '3bc1edfc-1f3f-54d7-aeb1-6b65e0cd6fda'::uuid)
ON CONFLICT (staff_member_id) DO NOTHING;

SELECT set_config('hms.trusted_op', '', false);
