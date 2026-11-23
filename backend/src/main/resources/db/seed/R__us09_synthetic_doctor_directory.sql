-- =============================================================================
-- HMS Community | Flyway yinelenebilir (repeatable) migration: US-09 demo dizini
--
-- YALNIZCA YEREL DEMO İÇİNDİR. Bu dosya varsayılan migration konumunda değildir;
-- yalnızca HMS_FLYWAY_LOCATIONS içine classpath:db/seed eklendiğinde çalışır
-- (compose.yaml yerel demoda bunu yapar). Üretim ve CI şeması bu veriyi içermez.
--
-- İçerik tamamen sentetiktir: departman adları genel tıp branşlarıdır, doktor adları
-- uydurmadır ve gerçek bir kişiye ait değildir. Doktorların kullanıcı hesabı yoktur;
-- yalnızca dizinde (doctors görünümü) listelenirler.
--
-- Yineleme güvenliği: Flyway bu dosyayı içerik değiştiğinde yeniden çalıştırır. Her
-- ekleme mevcut kaydı (aynı kimlik veya aynı departman adı) atladığı için tekrar
-- çalıştırma yinelenen satır üretmez ve mevcut kayıtları değiştirmez veya silmez.
-- "Acil Tıp" bilerek doktorsuz bırakılmıştır (boş sonuç senaryosu, FR-09.3).
-- =============================================================================

-- Demo verisi bir kullanıcı işlemi değil sistem kurulumudur; izin korumaları bu işlem
-- (transaction) süresince atlanır. Denetim tetikleyicileri eklemeleri yine kaydeder.
SELECT set_config('hms.trusted_op', 'migration', true);

CREATE TEMPORARY TABLE seed_departments (id uuid, name text) ON COMMIT DROP;
INSERT INTO seed_departments (id, name) VALUES
('5e0d0000-0000-4000-8000-000000000101', 'Kardiyoloji'),
('5e0d0000-0000-4000-8000-000000000102', 'Nöroloji'),
('5e0d0000-0000-4000-8000-000000000103', 'Genel Cerrahi'),
('5e0d0000-0000-4000-8000-000000000104', 'Ortopedi ve Travmatoloji'),
('5e0d0000-0000-4000-8000-000000000105', 'İç Hastalıkları'),
('5e0d0000-0000-4000-8000-000000000106', 'Göz Hastalıkları'),
('5e0d0000-0000-4000-8000-000000000107', 'Çocuk Sağlığı ve Hastalıkları'),
('5e0d0000-0000-4000-8000-000000000108', 'Acil Tıp');

CREATE TEMPORARY TABLE seed_doctors (id uuid, title text, first_name text, last_name text, department text)
    ON COMMIT DROP;
INSERT INTO seed_doctors (id, title, first_name, last_name, department) VALUES
('5e0d0000-0000-4000-8000-000000000201', 'Prof. Dr.',       'Ayla',   'Örnekoğlu',     'Kardiyoloji'),
('5e0d0000-0000-4000-8000-000000000202', 'Uzm. Dr.',        'Kerem',  'Denemeci',      'Kardiyoloji'),
('5e0d0000-0000-4000-8000-000000000203', 'Doç. Dr.',        'Selin',  'Kurgulu',       'Nöroloji'),
('5e0d0000-0000-4000-8000-000000000204', 'Uzm. Dr.',        'Tarık',  'Taslakçı',      'Nöroloji'),
('5e0d0000-0000-4000-8000-000000000205', 'Op. Dr.',         'Melis',  'Sentetikçi',    'Genel Cerrahi'),
('5e0d0000-0000-4000-8000-000000000206', 'Op. Dr.',         'Okan',   'Modelci',       'Genel Cerrahi'),
('5e0d0000-0000-4000-8000-000000000207', 'Doç. Dr.',        'Berk',   'Prototipoğlu',  'Ortopedi ve Travmatoloji'),
('5e0d0000-0000-4000-8000-000000000208', 'Op. Dr.',         'Nil',    'Örnektaş',      'Ortopedi ve Travmatoloji'),
('5e0d0000-0000-4000-8000-000000000209', 'Uzm. Dr.',        'İlkay',  'Denemeoğlu',    'İç Hastalıkları'),
('5e0d0000-0000-4000-8000-00000000020a', 'Prof. Dr.',       'Cem',    'Kurgusal',      'İç Hastalıkları'),
('5e0d0000-0000-4000-8000-00000000020b', 'Uzm. Dr.',        'Işıl',   'Taslaksoy',     'İç Hastalıkları'),
('5e0d0000-0000-4000-8000-00000000020c', 'Dr. Öğr. Üyesi',  'Deniz',  'Modeloğlu',     'Göz Hastalıkları'),
('5e0d0000-0000-4000-8000-00000000020d', 'Uzm. Dr.',        'Ece',    'Sentetikoğlu',  'Çocuk Sağlığı ve Hastalıkları'),
('5e0d0000-0000-4000-8000-00000000020e', 'Doç. Dr.',        'Umut',   'Örnekçi',       'Çocuk Sağlığı ve Hastalıkları');

-- Aynı kimlikte veya aynı adda (büyük/küçük harf duyarsız) bir departman varsa atlanır.
INSERT INTO departments (id, name)
SELECT s.id, s.name
FROM seed_departments s
WHERE NOT EXISTS (SELECT 1 FROM departments d WHERE d.id = s.id OR lower(d.name) = lower(s.name));

INSERT INTO staff_members (id, role_id, first_name, last_name, title)
SELECT s.id, r.id, s.first_name, s.last_name, s.title
FROM seed_doctors s
JOIN roles r ON r.code = 'Physician'
WHERE NOT EXISTS (SELECT 1 FROM staff_members m WHERE m.id = s.id);

-- Yalnızca hâlâ hekim olan ve henüz departmanı olmayan seed doktorları atanır;
-- sonradan yönetici tarafından değiştirilmiş kayıtlara dokunulmaz.
INSERT INTO doctor_departments (staff_member_id, department_id)
SELECT s.id, d.id
FROM seed_doctors s
JOIN staff_members m ON m.id = s.id
JOIN roles r ON r.id = m.role_id AND r.code = 'Physician'
JOIN departments d ON lower(d.name) = lower(s.department) AND d.is_active
WHERE NOT EXISTS (SELECT 1 FROM doctor_departments x WHERE x.staff_member_id = s.id);

SELECT set_config('hms.trusted_op', '', true);
