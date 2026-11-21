# Sprint 2 ön yüz ve backend entegrasyonu

| Bilgi | Değer |
| --- | --- |
| Yerel worktree | `C:\Users\ardas\Projects\SoftwareEngineering321-Sprint2-Integration-Current` |
| Yerel dal | `feature/sprint2-frontend-integration-current` |
| Doğrulanan `origin/develop` tabanı | `4570f80bc96ed39de293c6509a7d1057ba6de193` |
| Önceki ön yüz kaynağı (US-08 dahil) | `fca4a2122115f45076b08ff077cd825f3d95a299` |
| Ön yüzün ortak atası | `fa9f79fbb492d838f5a16559403815ee17eaacaf` |

Resmî tabanda ortak atadan beri çakışan bir ön yüz farkı bulunmadı. Önceki entegrasyon worktree’sindeki 77 dosyanın içeriği tamamlanmış ön yüz kaynağıyla karşılaştırıldı. Ön yüz yeni tabana dosya bazında aktarıldı. Önceki worktree’ler ve dal uçları korundu. #237–#242, #246 ve #230 resmî tabana birleşmiş durumda. [PostgreSQL PR #246](https://github.com/HabibYazar/SoftwareEngineering321/pull/246) · [Ön yüz SRS PR #230](https://github.com/HabibYazar/SoftwareEngineering321/pull/230)

## Kullanıcı hikâyeleri ve HTTP sözleşmesi

| Hikâye | İstek ve yanıt | Doğrulama |
| --- | --- | --- |
| US-01 Personel girişi ve çıkışı | `POST /api/v1/auth/login` → `200 { accessToken, tokenType, role, expiresAt }` · `POST /api/v1/auth/logout` → `204` | Java/OpenAPI incelemesi, sentetik HTTP, demo |
| US-02 Rol tabanlı gezinme | Ayrı izin uç noktası bulunmuyor. Girişteki `role` yerel gezinmeyi belirliyor. Sunucu her korumalı isteği yeniden yetkilendiriyor. | Yedi rol için rota testleri ve sentetik erişim yanıtları |
| US-06 Hasta kaydı | `POST /api/v1/patients` → `201 Patient` | Java/OpenAPI incelemesi, sentetik HTTP, demo |
| US-07 Hasta arama | `GET /api/v1/patients?nationalId=...` veya `GET /api/v1/patients?name=...` → `200 Patient[]` | Java/OpenAPI incelemesi, sentetik HTTP, demo |
| US-08 Hasta profili | `GET /api/v1/patients/{patientId}` ve `PUT /api/v1/patients/{patientId}` → `200 Patient` | Java/OpenAPI incelemesi, sentetik HTTP, demo |
| US-09 Doktor dizini | Açık #229 PR’ı `GET /api/v1/staff` ve `GET /api/v1/staff?department=...` tanımlıyor. Yanıt `{ id, displayName, department }[]`. | Hazır HTTP istemcisi sentetik testlerle doğrulandı. Uygulama şu anda dizini yalnızca açık geliştirme demosunda gösteriyor. |
| US-03 Sunucu denetimi | Ön yüzde yeni uç nokta veya denetim deposu yok. Var olan giriş, çıkış ve hasta işlemleri sunucudaki denetim akışını tetikler. | Kaynak incelemesi. PostgreSQL’deki denetim sonuçları canlı çalıştırılmadı. |

### Giriş, oturum ve erişim

Giriş gövdesi `{ username, password }`. Korumalı çağrılarda `Authorization: Bearer <accessToken>` kullanılır. Token opaktır, bellekte tutulur ve `expiresAt` doğrulanır. Sayfa yeniden yüklendiğinde yeniden giriş gerekir. Tarayıcının kalıcı deposuna yalnız tema tercihi yazılır. Geçerli oturuma ait `401` oturumu kapatır. `403` oturumu korur. İptal edilmiş veya eski bir isteğin `401` yanıtı daha yeni oturumu kapatamaz. Çıkışta `401`, oturumun sunucuda zaten geçersiz olduğu durum olarak ele alınır.

`StaffRole` ve `Permission` yedi personel rolünü ve izin adlarını tanımlar. `V3__patients.sql` ile `has_permission` kaynakları hasta kayıt, arama, okuma ve güncelleme için Receptionist ve Administrator erişimini doğruluyor. Kalan roller ve bilinmeyen izinler mevcut hasta rotalarında varsayılan olarak reddedilir. Giriş yanıtı ayrıntılı izin listesi içermediğinden yerel menü doğrulanmış başlangıç izinlerine dayanır. Geçerli sunucu erişim kararını veritabanı yapar. Doktor demosu Receptionist rolünde kalır.

İlgili kaynaklar `api/openapi.yaml`, `AuthController.java`, `LoginRequest.java`, `LoginResponse.java`, `AuthenticationService.java`, `AccessTokenService.java`, `JdbcStaffAccountDirectory.java`, `JdbcStaffSessionStore.java`, `HttpSecurityConfiguration.java`, `StaffRole.java` ve rol/izin migrasyonlarıdır.

### Hasta gövdeleri ve hata durumları

- `NewPatient` alanları `firstName`, `lastName`, `nationalId`, `dateOfBirth`, `gender`, `phone`, `address`, `conditionLevel` ve varsa `relativeInformation`. `Patient` bu alanlara UUID biçimli `id` ekler. Kayıtta `201` yanıtındaki ulusal kimlik gönderilen değerle eşleşmelidir. `409` çift kimlik uyarısı verir.
- Arama çağrısı tek bir ölçüt gönderir. Kimlik tam 11 rakamla aranır. Kısmi ad araması, birden fazla sonuç ve boş `[]` desteklenir. Kimlik aramasından farklı kimlik veya birden fazla kayıt dönerse yanıt geçersiz sayılır. Güncel sorgudan önceki istekler arayüzü değiştiremez.
- Tam değiştirme yapan `PUT` gövdesi bütün düzenlenebilir alanları içerir. `id` ve `nationalId` yalnız yanıttadır. Mild veya Moderate profilde mevcut yakın bilgisi korunur. Kullanıcı yakını açıkça kaldırırsa `relativeInformation` gövdeden çıkarılır. Advanced için `fullName`, `relationship` ve `phone` zorunludur. İki form da `1900-01-01` alt sınırını, geçerli takvim gününü, telefon biçimini ve uzunlukları doğrular. Kaydedilen yanıtta hem UUID hem ulusal kimlik kontrol edilir.
- `application/problem+json` alan hataları ön yüzde sabit İngilizce mesajlara eşlenir. Kullanıcıya ham yanıt gövdesi gösterilmez. `400`, `401`, `403`, `404`, `409`, `500` ve `503` ilgili akışta ele alınır. HTTP istemcilerinde 15 saniyelik zaman aşımı vardır. Bozuk yanıt veya ağ hatası sentetik başarı üretmez. `503 INTEGRATION_PENDING` veritabanı bağlantısı yapılandırılmadığında görülebilir.
- Okunan kaynaklar `PatientController.java`, `PatientRequest.java`, `PatientDetails.java`, `RelativeInformation.java`, `PatientValidator.java`, `PatientService.java`, `JdbcPatientRepository.java`, `ApiExceptionHandler.java`, `ApiProblems.java` ve `FieldViolation.java`.

### PostgreSQL ve US-03

Birleşen #246, yapılandırılmış PostgreSQL veri kaynağı varsa personel hesabı ve oturumu, hasta deposu ve kalıcı denetim adaptörlerini seçer. `application.yml` içindeki `HMS_DB_URL` ayarı ve Flyway migrasyonları okundu. `AuditService.java`, `JdbcAuditEventStore.java` ve SQL denetim işlevleri giriş, çıkış, hasta görüntüleme ve değişiklik olayları için kaynak olarak incelendi. Hasta güncellemesinin önceki ve sonraki değerleri sunucuya aittir. Ön yüz bu kayıtları üretmez veya tarayıcı deposuna yazmaz.

Doğrulama sırasında hazır, yetkilendirilmiş sentetik hesap ve uyumlu yerel PostgreSQL backend’i bulunmadı. Java, Docker ve Flyway başlatılmadı. Veritabanı işlemi yapılmadı. Gerçek giriş, rol yetkilendirmesi, hasta kaydı/güncellemesi ve kalıcı denetim sonuçları uygun bir düzende doğrulanmayı bekliyor.

### US-09 açık PR’ları ve şema uyuşmazlığı

#229’daki `StaffController.java`, `StaffService.java`, `StaffRepository.java` ve testleri incelendi. Depo `clinicians` tablosundaki `row_num`, `id`, `display_name` ve `department` alanlarını kullanır. Departman filtresi eşitlik ile çalışır. Ayrı bir departman uç noktası yoktur. Ön yüz seçenekleri mevcut klinisyenlerden türetir ve klinisyensiz departmanları gösteremez. PR’daki herkese açık güvenlik kuralı resmî tabanın varsayılan ret yaklaşımıyla uyumlu hale getirilmelidir. [Doktor dizini PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229)

#228’deki `clinicians.csv`, `departments.csv` ve `init_and_seed.sql` yalnızca okundu. Resmî V1 şemasında `staff_members`, `doctor_departments` ve `departments(id, name, is_active, …)` var. `clinicians` yok. Seed betiği aynı `departments` tablosunu `row_num`, `group_name`, `clinician_count` ve `source_url` sütunlarıyla bekliyor. `CREATE TABLE IF NOT EXISTS` mevcut tabloya eksik sütun eklemez ve ardından gelen `COPY` uyumsuz kalır. İki PR’ın resmî şema, rol ve veri yükleme kurallarıyla uyumu backend tarafında çözülmelidir. Güncel tabanda istemci doktora ait HTTP çağrısı göndermeden uç noktanın kullanılamadığını bildirir. [Tohum veri PR #228](https://github.com/HabibYazar/SoftwareEngineering321/pull/228)

## Yerel doğrulama

PowerShell’de:

```powershell
Set-Location 'C:\Users\ardas\Projects\SoftwareEngineering321-Sprint2-Integration-Current\frontend'
# node_modules henüz yoksa mevcut kilitli bağımlılıkları kurun.
# npm.cmd ci
npm.cmd test
node --test tests/backend-integration.test.mjs
npm.cmd run build
git diff --check
```

Üretim derlemesinin oluşturduğu `frontend/tsconfig.app.tsbuildinfo` bu tabanın Git kurallarında takip dışı görünebilir. Yerel dosya kapsamı kontrolünden önce kendi derlemenizin ürettiği bu önbelleği depo dışına taşıyabilirsiniz.

Demo için aynı dizinde beş bayrağı geliştirme oturumunda birlikte açın. Ön yüz README dosyasındaki sentetik Receptionist hesabıyla giriş yapın. Doktor dizininde filtre ve yenilemeyi, hasta formunda sentetik kayıt, arama ve profil düzenlemeyi deneyin.

```powershell
$env:VITE_AUTH_MOCK = 'true'
$env:VITE_STAFF_MOCK = 'true'
$env:VITE_PATIENT_REGISTRATION_MOCK = 'true'
$env:VITE_PATIENT_SEARCH_MOCK = 'true'
$env:VITE_PATIENT_PROFILE_MOCK = 'true'
npm.cmd run dev -- --host 127.0.0.1
```

Gerçek HTTP modunu ancak yetkili bir sentetik hesap ve önceden çalışan uyumlu yerel backend varsa deneyin. Yukarıdaki beş değeri `'false'` yapıp Vite’ı yeniden başlatın. Korunan Vite ayarı `/api` isteklerini `http://localhost:8080` adresine yönlendirir. Demo hesabını gerçek servise göndermeyin. Başarılı canlı doğrulama için kayıt, sorgu, güncelleme, çıkış ve sunucu tarafı denetim sonuçları ayrı ayrı kontrol edilmelidir. Bu adımlar veritabanı hazırlama veya sunucu başlatma yetkisi vermez.

## Test ve Git sonuçları

- Tam otomatik paket 95/95, odaklı HTTP sözleşme paketi 8/8 geçti. Odaklı testler tam paketin içindedir.
- TypeScript denetimi ve Vite üretim derlemesi geçti. Kilitli mevcut bağımlılıklar yeniden kullanıldı. Üretim çıktısında demo hesapları, demo parolası ve sentetik kayıt örnekleri için metin taraması yapıldı.
- Yalnızca 127.0.0.1 üzerinde çalışan geçici sentetik HTTP düzeneğinde giriş ve çıkış, 503 geri bildirimi, hasta kaydı, tam kimlik araması, UUID ile profil, güncelleme, 503 sonrası taslak koruması ve güncellenen adla arama geçti. Bu düzenek Spring Boot veya bir veritabanı kullanmadı.
- Uygulamanın geliştirme demosunda Administrator hasta menüsü, Physician doğrudan rota reddi ve Receptionist doktor filtresi ile yenileme geçti. Klavye ile form gönderimi, tema ve menü kontrolleri çalıştı. Profilde 768 px koyu ve 390 px açık görünüm yatay taşma göstermedi. Tema tercihi yeniden yüklemede korundu.
- Git kapsamı dört izlenen dosya değişikliği ve 77 yeni dosyadan oluşur. İndeks boş. İzlenen ve yeni dosyalarda whitespace denetimi uygulandı. Korunan yollar, önceki worktree’ler ve dal uçları korundu.

## Entegrasyon için güncellenen dosyalar

Aşağıdaki yollar, tamamlanmış US-08 ön yüz kaynak ağacına göre eklenen veya entegrasyon için uyarlanan dosyalardır.

- `frontend/README.md`
- `frontend/docs/backend-integration/README.md`
- `frontend/src/apiProblem.ts`
- `frontend/src/auth/AuthProvider.tsx`
- `frontend/src/auth/authorizedRequest.ts`
- `frontend/src/auth/httpAuthService.ts`
- `frontend/src/auth/mockAuthService.ts`
- `frontend/src/auth/permissions.ts`
- `frontend/src/auth/session.ts`
- `frontend/src/auth/types.ts`
- `frontend/src/main.tsx`
- `frontend/src/patients/PatientRegistrationPage.tsx`
- `frontend/src/patients/httpRegistrationService.ts`
- `frontend/src/patients/patientProblem.ts`
- `frontend/src/patients/profile/PatientProfilePage.tsx`
- `frontend/src/patients/profile/httpProfileService.ts`
- `frontend/src/patients/profile/model.ts`
- `frontend/src/patients/profile/saveRunner.ts`
- `frontend/src/patients/search/httpSearchService.ts`
- `frontend/src/patients/search/model.ts`
- `frontend/src/patients/types.ts`
- `frontend/src/patients/validation.ts`
- `frontend/src/staff/service.ts`
- `frontend/src/staff/types.ts`
- `frontend/src/staff/useClinicians.ts`
- `frontend/tests/auth.test.mjs`
- `frontend/tests/backend-integration.test.mjs`
- `frontend/tests/patient-profile.test.mjs`
- `frontend/tests/patient-search.test.mjs`
- `frontend/tests/permissions.test.mjs`
- `frontend/tests/registration.test.mjs`

## Resmî tabana göre tam dosya envanteri

81 dosya, önceki hikâyelerin aktarılan kaynaklarını, belgelerini, testlerini ve entegrasyon değişikliklerini içerir. Mevcut tasarım, gezinme, açık/koyu tema ve hasta formlarının yapısı korundu. Herhangi bir yeni npm bağımlılığı eklenmedi. Paket bildirimindeki komut farkı test betiğidir.

- `frontend/.env.example`
- `frontend/README.md`
- `frontend/docs/TECH_STACK.md`
- `frontend/docs/US-01-frontend.md`
- `frontend/docs/US-02-frontend.md`
- `frontend/docs/US-06-frontend.md`
- `frontend/docs/US-07-frontend.md`
- `frontend/docs/US-08-frontend.md`
- `frontend/docs/US-09-frontend.md`
- `frontend/docs/backend-integration/README.md`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/App.tsx`
- `frontend/src/apiProblem.ts`
- `frontend/src/auth/AuthProvider.tsx`
- `frontend/src/auth/LoginPage.tsx`
- `frontend/src/auth/ProtectedRoute.tsx`
- `frontend/src/auth/authorizedRequest.ts`
- `frontend/src/auth/httpAuthService.ts`
- `frontend/src/auth/mockAuthService.ts`
- `frontend/src/auth/permissions.ts`
- `frontend/src/auth/routing.ts`
- `frontend/src/auth/service.ts`
- `frontend/src/auth/session.ts`
- `frontend/src/auth/types.ts`
- `frontend/src/components/AppLayout.tsx`
- `frontend/src/components/NavigationControls.tsx`
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/ui.tsx`
- `frontend/src/main.tsx`
- `frontend/src/navigation/history.ts`
- `frontend/src/navigation/useAppNavigation.ts`
- `frontend/src/pages/ArchitecturePage.tsx`
- `frontend/src/pages/WorkspacePage.tsx`
- `frontend/src/patients/PatientDetailFields.tsx`
- `frontend/src/patients/PatientRegistrationPage.tsx`
- `frontend/src/patients/httpRegistrationService.ts`
- `frontend/src/patients/mockPatientStore.ts`
- `frontend/src/patients/mockRegistrationService.ts`
- `frontend/src/patients/patientProblem.ts`
- `frontend/src/patients/profile/PatientProfilePage.tsx`
- `frontend/src/patients/profile/httpProfileService.ts`
- `frontend/src/patients/profile/mockProfileService.ts`
- `frontend/src/patients/profile/model.ts`
- `frontend/src/patients/profile/profile.css`
- `frontend/src/patients/profile/saveRunner.ts`
- `frontend/src/patients/profile/service.ts`
- `frontend/src/patients/registration.css`
- `frontend/src/patients/search/PatientSearchPage.tsx`
- `frontend/src/patients/search/httpSearchService.ts`
- `frontend/src/patients/search/mockSearchService.ts`
- `frontend/src/patients/search/model.ts`
- `frontend/src/patients/search/search.css`
- `frontend/src/patients/search/searchRunner.ts`
- `frontend/src/patients/search/service.ts`
- `frontend/src/patients/service.ts`
- `frontend/src/patients/types.ts`
- `frontend/src/patients/useUnsavedPatientForm.ts`
- `frontend/src/patients/validation.ts`
- `frontend/src/staff/DoctorDirectory.tsx`
- `frontend/src/staff/directory.css`
- `frontend/src/staff/filtering.ts`
- `frontend/src/staff/httpStaffService.ts`
- `frontend/src/staff/mockStaffService.ts`
- `frontend/src/staff/service.ts`
- `frontend/src/staff/types.ts`
- `frontend/src/staff/useClinicians.ts`
- `frontend/src/style.css`
- `frontend/src/theme/ThemeToggle.tsx`
- `frontend/src/theme/preference.ts`
- `frontend/src/theme/tokens.css`
- `frontend/src/vite-env.d.ts`
- `frontend/tests/auth.test.mjs`
- `frontend/tests/backend-integration.test.mjs`
- `frontend/tests/fixtures/staff-refresh-server.mjs`
- `frontend/tests/navigation.test.mjs`
- `frontend/tests/patient-profile.test.mjs`
- `frontend/tests/patient-search.test.mjs`
- `frontend/tests/permissions.test.mjs`
- `frontend/tests/registration.test.mjs`
- `frontend/tests/staff.test.mjs`
