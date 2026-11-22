-- =============================================================================
-- HMS Community | Flyway migration V4
-- US-09 (#201, #204): doktor dizini görüntüleme izni.
--
-- GET /api/v1/departments ve GET /api/v1/staff uç noktaları, diğer korumalı uç
-- noktalarla aynı biçimde veritabanındaki rol–izin matrisine bağlanır. SRS FR-09.10
-- gereği izin yalnızca Receptionist rolüne verilir; Administrator, V1'deki
-- permissions_grant_administrator tetikleyicisiyle her yeni izni otomatik alır.
--
-- Veri politikası: bu migration kişi, doktor veya departman satırı EKLEMEZ.
-- Sentetik demo dizini isteğe bağlı db/seed konumundadır (yalnızca yerel demo).
-- =============================================================================

-- Referans veri eklemesi izin korumalarına takılmasın diye (V1–V3 ile aynı yöntem).
SELECT set_config('hms.trusted_op', 'migration', false);

INSERT INTO permissions (id, code, module, story, description_tr, description_en) VALUES
(10, 'DOCTOR_DIRECTORY_VIEW', 'staff', 'US-09',
 'Departmanları ve departmana göre doktorları listeleme', 'List departments and doctors by department');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'DOCTOR_DIRECTORY_VIEW'
WHERE r.code = 'Receptionist';

SELECT set_config('hms.trusted_op', '', false);
