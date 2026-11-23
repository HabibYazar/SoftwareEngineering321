# Sprint 2 ön yüz ve backend entegrasyonu

Bu kılavuz, yerel `feature/sprint2-frontend-polish-admin-demo` dalında `6c2ec00302ce15c34beac1893caa1815334046f5` tabanı üzerine eklenen iyileştirmeleri kapsar. Önceki entegrasyon PR'ları #247 ve #250 ile GitHub sürüm bilgileri kullanıcı tarafından sağlandı. Bu çalışmada uzak depo sorgulanmadı. Birleşmiş sözleşmeler yerel OpenAPI, Java kaynakları ve Flyway şemasından salt okunur kontrol edildi.

## Entegrasyon matrisi

| Hikâye | Metot ve uç nokta | Backend durumu | UI, HTTP servisi ve demo | Erişim | Test ve kalan iş |
| --- | --- | --- | --- | --- | --- |
| US-01 | `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` | Birleşmiş | `LoginPage`, `httpAuthService`, `mockAuthService` | Giriş herkese açık. Çıkış ve iş istekleri geçerli Bearer oturumunu kullanır. | Enjekte HTTP, oturum süresi ve çıkış regresyonları. Canlı oturum iptali bu çalışmada denenmedi. |
| US-02 | Girişte gelen `role`, ayrı izin endpoint'i yok | Birleşmiş rol ve yetkilendirme | `Sidebar`, `WorkspacePage`, `ProtectedRoute`, `authorizedRequest`. Yönetici demosu matrisinin ayrı modeli var. | Hasta işlemleri Administrator ve Receptionist. Dizin Receptionist. Yönetici demoları Administrator. | Tüm roller, doğrudan rota, 401/403 ve geçmiş testleri. Sunucu izinlerin son otoritesidir. |
| US-06 | `POST /api/v1/patients` | Birleşmiş | `PatientRegistrationPage`, `httpRegistrationService`, `mockRegistrationService` | Yetkili Bearer | Kayıt sağlama denetimi, çift kayıt, 201 yanıtı ve UUID başarı bağlantısı doğrulandı. Canlı kayıt denenmedi. |
| US-07 | `GET /api/v1/patients?nationalId=…` veya `?name=…` | Birleşmiş arama. Tam liste endpoint'i yok. | `PatientSearchPage`, `httpSearchService`, `mockSearchService` | Yetkili Bearer. Oturum özeti ve demo listesi tarayıcı belleğinde. | Tek ölçüt, boş sonuç, iptal, yeni sentetik kayıt, gerçek modda genel liste isteğinin yokluğu. |
| US-08 | `GET /api/v1/patients/{patientId}`, `PUT /api/v1/patients/{patientId}` | Birleşmiş | `PatientProfilePage`, `httpProfileService`, `mockProfileService` | Yetkili Bearer ve rota UUID'si | Değişmez kimlik, tam güncelleme, yakın kaldırma, başarısız kayıt ve gezinme iptali testleri. Canlı PUT ve veritabanı denetim kaydı bekliyor. |
| US-03 | Giriş, çıkış ve hasta işlemlerinin sunucu tarafı olayları | Birleşmiş yazma yolları | Mevcut istemciler işlemleri doğal yollarından çağırır. | Sunucu tarafı | Denetim çıktıları kaynak kodda incelendi. PostgreSQL kaydı bu çalışmada çalıştırılmadı. |
| US-04 | Henüz onaylı okuma veya filtre uç noktası yok | Eksik | `AuditLogsPage`, `AuditService`, `mockAuditService`. HTTP servisi yok. | Yalnız Administrator ve açık DEV demo bayrağı | Tarih, actor ve action filtreleri, sıralama, boş sonuç, yetki testleri. Sözleşme ve sunucu yetkisi bekliyor. |
| US-05 | Henüz onaylı hesap yönetimi uç noktası yok | Eksik | `StaffManagementPage`, `StaffManagementService`, `mockStaffManagementService`. HTTP servisi yok. | Yalnız Administrator ve açık DEV demo bayrağı | CRUD benzeri demo işlemleri, reset talebi, son yönetici, bölüm ataması, izin izolasyonu ve sıfır HTTP yazma. Gerçek yönetim sözleşmesi bekliyor. |
| US-09 klinisyen | `GET /api/v1/staff`, `GET /api/v1/staff?department={name}` | #229 birleşmemiş | `DoctorDirectory`, `httpStaffService`, `mockStaffService` | Receptionist. DEV HTTP önizlemesi mevcut Bearer oturumuyla çağrılır. | Sentetik HTTP, isimle filtre, boş sonuç, iptal ve bağımsız hata. Tablo eşleşmesi ve üretim yetkisi bekliyor. |
| US-09 departman | `GET /api/v1/departments` | #251 birleşmemiş, yanıt farkı var | `httpDepartmentService` ve ayrı `pr251DepartmentPreview` adaptörü. | Receptionist ön yüz rotası ve ayrı DEV önizleme bayrağı | Kanonik ve geçici yanıt, tekrarlar, boş departman ve kesinti testleri. Kalıcı UUID ve kanonik DTO bekleniyor. |
| Architecture | `GET /api/v1/health` | Yerel sözleşmede mevcut | `ArchitecturePage`, mevcut sağlık okuması | Açık rota | Görünüm korundu. Canlı sağlık kontrolü bu çalışmada yapılmadı. |

## Hasta sözleşmesi ve veri sınırı

Giriş `{ username, password }` gönderir ve `{ accessToken, tokenType, role, expiresAt }` alır. Token bellekte tutulur. Aynı oturumun `401` yanıtı oturumu kapatır. `403` açık oturumu korur. Tema dışında hasta, parola, oturum veya denetim verisi tarayıcı depolamasına yazılmaz.

`NewPatient`, ad, soyad, `nationalId`, doğum tarihi, cinsiyet, telefon, adres, koşul seviyesi ve isteğe bağlı `relativeInformation` içerir. `Patient` yanıtına UUID `id` eklenir. Profilin `PUT` gövdesi düzenlenebilir alanların tamamını gönderir. Kimlik numarası ve `id` gövdeye girmez. Mild ve Moderate için kullanıcı açıkça kaldırırsa yakın alanı atlanır. Advanced seviyesinde üç yakın alanı zorunludur. `400` alan hataları ve `409` çift kimlik hatası korunur.

Yeni US-06 kaydında ön yüz tam 11 ASCII rakam, sıfır olmayan ilk hane ve 10. ile 11. sağlama hanelerini zorunlu tutar. Geçersiz giriş POST'a ulaşmaz. Java, OpenAPI ve veritabanı sözleşmesi hâlâ 11 rakam denetler. Eski kimliklerle US-07 araması ve US-08 profil yükleme/düzenleme çalışır. Profil kimliği değişmez. Ürün Sahibi onayı ve sunucu tarafı zorunluluğu ayrı kararlardır. Testler yeni kayıt için deterministik sentetik değerler, geriye uyumluluk için mevcut sentetik kimlikleri kullanır. Sağlama, matematiksel tutarlılığı denetler. Resmî doğrulama hizmeti çağrılmaz. [US-06 kimlik denetimi](../US-06-frontend.md#national-id-validation-boundary)

Cinsiyet seçimi Male, Female, Other ve Prefer not to disclose seçeneklerini sunar. Son seçenek `Not disclosed` değerini gönderir. Backend sözleşmesi serbest string alanıdır. Ürün Sahibi seçenekleri henüz onaylamadı. Eski bilinmeyen değer görünür kalır ve kullanıcı seçim yapana kadar gönderilemez. Hasta ve yakın telefonları `05XX XXX XX XX` biçiminde gösterilir. Fazla haneli yapıştırma kesilmeden reddedilir. POST ve PUT, geçerli yerel numarayı `+905XXXXXXXXX` biçiminde gönderir. Daha geniş eski telefonlar düzeltilmeden kaydedilmez.

## Yılmaz'ın yerel referansları ve engeller

- **#229**, `feature/204-expose-department-and-filtered-doctor-lists`, `95d5ebbed979f07f90d217023d0f8b6f65fabb53`. Yerel `backend/src/main/java/org/hmscommunity/staff/StaffController.java`, `StaffService.java`, `StaffRepository.java`, `backend/src/test/java/org/hmscommunity/staff/StaffControllerTest.java` ve `docs/architecture/US-09-backend-architecture.md` referansları. Dizi `{ id, displayName, department }` döndürür. Sorgu parametresi departman adıdır. `clinicians` tablosu mevcut V1'de yok, karşılık gelen resmî yapı `staff_members`, `departments` ve `doctor_departments`. PR'daki public GET önerisinin üretim yetkisi ayrıca onaylanmalıdır. [PR #229](https://github.com/HabibYazar/SoftwareEngineering321/pull/229)
- **#251**, `spec/api-departments-spec`, `01706b5a730a618fc463096c8f1eeb506a6d163b`. Commit yerelde yok, aşağıdaki bilgiler kullanıcının verdiği sözleşmeden alındı. İlgili yollar `backend/src/main/java/org/hmscommunity/departments/DepartmentsController.java`, `DepartmentsService.java`, `DepartmentsRepository.java` ve `api/openapi.yaml`. Kanonik `{ id, name }[]` yerine geçici controller `{ id, department }[]` döndürüyor, 10 örnek ve her istekte yeni UUID üretiyor, repository kataloğunu kullanmıyor. `/departments` adresi kullanılmaz. Son sürüm kanonik alanı, sabit UUID'leri ve onaylı sunucu yetkilendirmesini sağlamalıdır. [PR #251](https://github.com/HabibYazar/SoftwareEngineering321/pull/251)
- **#227** `devops/221-docker-containerization` yalnızca altyapı referansıdır. **#228** Flyway sonrasında birleşmeden kapatılmış eski seed çalışmasıdır. Ön yüz hiçbir CSV veya eski seed varsaymaz. [PR #227](https://github.com/HabibYazar/SoftwareEngineering321/pull/227) · [PR #228](https://github.com/HabibYazar/SoftwareEngineering321/pull/228)

## Yerel doğrulama

[Ön yüz README](../../README.md), açık DEV demo bayrakları ve sentetik hesapları içerir. US-09 için `VITE_STAFF_MOCK=false` ayarlanır ve `VITE_STAFF_HTTP_PREVIEW=true` ile `VITE_DEPARTMENTS_HTTP_PREVIEW=true` birbirinden bağımsız seçilir. Geçici #251 biçimi için ayrıca `VITE_DEPARTMENTS_PR251_COMPAT=true` gerekir. Varsayılanda önizleme kapalıdır. Başarılı departman kataloğu sıfır doktorlu bölümleri de gösterir. Ayrı departman servisi çalışmazsa kullanılabilir doktor listesinden isimler türetilir ve hata açıkça gösterilir. İki API birbirinin kesintisini gizlemez.

`npm.cmd test` tüm Node ve yerel Vite bileşen testlerini çalıştırır. `npm.cmd run build` TypeScript ve Vite üretim derlemesini doğrular. Mevcut kilitli sürümler ve kurulu araçlar tekrar kullanıldı. [Teknoloji sürümleri](../TECH_STACK.md)

- Kimlik denetimi sonrasında tam paket 123/123 geçti. Önceki 117 test ilk 11 rakam kuralından yeni kayıt kuralına uyarlanan sentetik girdilerle korundu ve 6 odaklı test eklendi. Kapsam ilk hane, iki sağlama hanesi, negatif mod, odak geri bildirimi, POST engeli, 409 ve eski kayıt uyumluluğunu içerir.
- Yerel telefon biçimi ve giriş sınırı eklendikten sonra 127/127 test geçti.
- TypeScript denetimi ve Vite 7.3.8 üretim derlemesi geçti. Tüm DEV bayrakları açık verilerek yapılan üretim derlemesi, taranan demo parola/veri işaretlerini ve US-09 önizleme GET adreslerini içermedi.
- Yerel Chrome denetiminde başı sıfır olan yeni kayıt girdisi odaktan çıkınca hata verdi. 12 haneli giriş kesilmedi ve düzeltilince hata yazarken kalktı. Dört İngilizce cinsiyet seçeneği görüldü. Eski sentetik kimlik tam aramayla bulundu ve profil kimliği korunarak demo kaydı yapıldı. Açık/koyu görünüm, dar düzende tek sütun ve açılır menü ile iki Administrator demo ekranı açılarak kontrol edildi. Düzeneğin oturumu sentetikti. Canlı Spring Boot/PostgreSQL, gerçek giriş ve yeni kaydın tam tarayıcı gönderimi bu denetimde denenmedi.
- Derleme ve testler doğrulanmış geçici ön yüz kopyasında yürütüldü. Paket dosyaları, derleme çıktıları veya TypeScript önbelleği değişiklik listesine dahil edilmedi.

## Bu artışta değişen ön yüz dosyaları

### Güncellenen

- `frontend/.env.example`
- `frontend/README.md`
- `frontend/docs/TECH_STACK.md`
- `frontend/docs/US-02-frontend.md`
- `frontend/docs/US-06-frontend.md`
- `frontend/docs/US-07-frontend.md`
- `frontend/docs/US-08-frontend.md`
- `frontend/docs/US-09-frontend.md`
- `frontend/docs/backend-integration/README.md`
- `frontend/src/App.tsx`
- `frontend/src/auth/AuthProvider.tsx`
- `frontend/src/auth/permissions.ts`
- `frontend/src/auth/routing.ts`
- `frontend/src/components/ui.tsx`
- `frontend/src/main.tsx`
- `frontend/src/pages/ArchitecturePage.tsx`
- `frontend/src/pages/WorkspacePage.tsx`
- `frontend/src/patients/PatientDetailFields.tsx`
- `frontend/src/patients/PatientRegistrationPage.tsx`
- `frontend/src/patients/mockPatientStore.ts`
- `frontend/src/patients/patientProblem.ts`
- `frontend/src/patients/profile/PatientProfilePage.tsx`
- `frontend/src/patients/profile/model.ts`
- `frontend/src/patients/registration.css`
- `frontend/src/patients/search/PatientSearchPage.tsx`
- `frontend/src/patients/search/mockSearchService.ts`
- `frontend/src/patients/search/model.ts`
- `frontend/src/patients/search/search.css`
- `frontend/src/patients/validation.ts`
- `frontend/src/staff/DoctorDirectory.tsx`
- `frontend/src/staff/directory.css`
- `frontend/src/staff/httpStaffService.ts`
- `frontend/src/staff/service.ts`
- `frontend/src/staff/types.ts`
- `frontend/src/staff/useClinicians.ts`
- `frontend/src/style.css`
- `frontend/src/theme/tokens.css`
- `frontend/src/vite-env.d.ts`
- `frontend/tests/backend-integration.test.mjs`
- `frontend/tests/patient-profile.test.mjs`
- `frontend/tests/patient-search.test.mjs`
- `frontend/tests/permissions.test.mjs`
- `frontend/tests/registration.test.mjs`
- `frontend/tests/staff.test.mjs`

### Eklenen

- `frontend/docs/US-04-frontend.md`
- `frontend/docs/US-05-frontend.md`
- `frontend/src/admin/admin.css`
- `frontend/src/admin/audit/AuditLogsPage.tsx`
- `frontend/src/admin/audit/mockAuditService.ts`
- `frontend/src/admin/audit/model.ts`
- `frontend/src/admin/service.ts`
- `frontend/src/admin/staff/StaffForm.tsx`
- `frontend/src/admin/staff/StaffManagementPage.tsx`
- `frontend/src/admin/staff/StaffSettings.tsx`
- `frontend/src/admin/staff/mockStaffManagementService.ts`
- `frontend/src/admin/staff/model.ts`
- `frontend/src/components/resourceRequest.ts`
- `frontend/src/components/useResource.ts`
- `frontend/src/patients/RegistrationSuccess.tsx`
- `frontend/src/patients/fieldFeedback.ts`
- `frontend/src/patients/formOptions.ts`
- `frontend/src/patients/recentPatients.ts`
- `frontend/src/patients/registrationAttempt.ts`
- `frontend/src/patients/search/PatientResultList.tsx`
- `frontend/src/patients/useFieldFeedback.ts`
- `frontend/src/staff/departments.ts`
- `frontend/src/staff/httpDepartmentService.ts`
- `frontend/src/staff/pr251DepartmentPreview.ts`
- `frontend/src/staff/previewMode.ts`
- `frontend/tests/admin-demo.test.mjs`
- `frontend/tests/directory-preview.test.mjs`
- `frontend/tests/fixtures/turkishNationalId.mjs`
- `frontend/tests/patient-polish.test.mjs`
- `frontend/tests/polish-ui.test.mjs`
- `frontend/tests/turkish-national-id.test.mjs`
