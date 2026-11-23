# HMS Community — Obsidian Night

Obsidian Night, günlük klinik işlerde veriyi okunur tutarken gündüz ve gece yüzeyleri arasında yumuşak bir görsel hiyerarşi kurar. H+ kimliği korunur. Giriş ve pano başlıklarında Georgia, veri ve kontrollerde sistem yazı tipi kullanılır. İstekte sözü edilen iki referans görseli ek klasöründe bulunamadı. Bu sürüm yazılı palet ve düzen tarifini esas alır.

## Palet

| Rol | Koyu | Açık |
| --- | --- | --- |
| Tuval | #090A10 | #EEF0F5 |
| Belge sonu | #0E1019 | #EEF0F5 |
| Yan panel | #11131D | #F9F9FC |
| Kart | #191A25 | #FFFFFF |
| Yükseltilmiş yüzey | #211F2D | #F7F6FB |
| Kenarlık | #302F40 | #D9D8E3 |
| Ana metin | #F0EFF5 | #242533 |
| İkincil metin | #A6A5B5 | #5B5C70 |
| Birincil düğme | #675F94 | #5B5084 |
| Hover | #766C9E | #6C6096 |
| Seçim yüzeyi | #39354F | #E9E5F4 |

Dekoratif renkler koyu lacivert, nötr gri ve mat mora dayanır. Turkuaz, teal, cyan, pembe, parlak mor, neon ve mint paletin dışında tutulur. Yeşil, kehribar ve sıcak kırmızı durum anlamı taşıyan başarı, uyarı ve hata bileşenlerine ayrılır.

## Arka plan ve yüzeyler

Koyu modda belge yüksekliğine yayılan dikey CSS geçişi ilk %34 boyunca #090A10 kalır ve sonda #0E1019’a ulaşır. İki geniş radyal katmanın en yüksek alfaları %5 ve %3,5’tir. Geçiş belgenin doğal kaydırmasıyla ilerler. JavaScript kaydırma dinleyicisi ve hareketli ışık kullanılmaz.

Koyu modda hafif cam etkisi yalnızca yan menü ve birincil pano kartındadır. Tarayıcı CSS desteği ve hareketten bağımsız saydamlık tercihi uygunsa %92–93 opak yüzey ve 5 px arka plan bulanıklığı kullanılır. Diğer durumda opak tema rengi devrededir. Formlar, hasta verileri ve yönetim tabloları opak yüzeylerde kalır. Girişin marka paneli ince halkalar ve palet içi ton geçişi kullanır.

## Bento, menü ve kontroller

İzinli ilk pano eylemi geniş ana karttadır. Diğer eylemler iki sütunda yer alır ve tek kalan kart satırı doldurur. Küçük ekranda kartlar tek sütuna iner. Eylemlerin metni, açıklaması, hedefi ve rol görünürlüğü Premium-UI ile aynıdır.

Yan menüde hizalı ikonlar, seçili sayfa için işaret, tema anahtarı ve oturum alanı bulunur. Formlarda opak giriş, belirgin bölüm başlığı ve ortak odak göstergesi kullanılır. Audit Logs tablosunun hover durumu saydamlık eklemez. Personel matrisi masaüstünde tablo, yaklaşık 816 px ve altında rol kartlarıdır. Doktor rehberi filtre ve sonuç ayrımını korur. Architecture sayfası aynı modülleri ve sağlık sonucunu yeni tema renkleriyle gösterir.

Düğmeler 170 ms geçişle hover sırasında hafifçe yükselir. Basılı durumda içe oturur. CSS `::after` dalgası düğme sınırında kırpılır ve işaretçi olaylarına katılmaz. Devre dışı durumda dalga kapalıdır. `prefers-reduced-motion` dalgayı, konum hareketini ve bekleme animasyonunu kapatır. Klavye odağı 3 px kalır. Örnek renk çiftlerinde ana metin ve zemin kontrastı açık temada 13,27:1 ve koyu temada 17,29:1. Birincil düğme metni hover dahil ölçülen dört açık ve koyu durumun tamamında en az 4,76:1.

## Aktarılan Premium-UI sunum dosyaları

Aşağıdaki 24 kaynak dosyası doğrulanan içerikleriyle kopyalandı. Premium-UI tasarım belgesi ve bütün iş kuralları mevcut yerlerinde korundu.

- `frontend/src/admin/admin.css`
- `frontend/src/admin/audit/AuditLogsPage.tsx`
- `frontend/src/admin/staff/StaffForm.tsx`
- `frontend/src/admin/staff/StaffManagementPage.tsx`
- `frontend/src/admin/staff/StaffSettings.tsx`
- `frontend/src/auth/LoginPage.tsx`
- `frontend/src/components/AppLayout.tsx`
- `frontend/src/components/NavigationControls.tsx`
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/ui.tsx`
- `frontend/src/pages/ArchitecturePage.tsx`
- `frontend/src/pages/WorkspacePage.tsx`
- `frontend/src/patients/PatientRegistrationPage.tsx`
- `frontend/src/patients/RegistrationSuccess.tsx`
- `frontend/src/patients/profile/PatientProfilePage.tsx`
- `frontend/src/patients/profile/profile.css`
- `frontend/src/patients/registration.css`
- `frontend/src/patients/search/PatientResultList.tsx`
- `frontend/src/patients/search/PatientSearchPage.tsx`
- `frontend/src/patients/search/search.css`
- `frontend/src/staff/DoctorDirectory.tsx`
- `frontend/src/staff/directory.css`
- `frontend/src/style.css`
- `frontend/src/theme/tokens.css`

## Obsidian Night için ayrıca değiştirilen dosyalar

- `frontend/src/admin/admin.css`
- `frontend/src/pages/WorkspacePage.tsx`
- `frontend/src/patients/registration.css`
- `frontend/src/patients/search/search.css`
- `frontend/src/staff/directory.css`
- `frontend/src/style.css`
- `frontend/src/theme/tokens.css`
- `frontend/docs/obsidian-night/DEPENDENCIES.md`
- `frontend/docs/obsidian-night/README.md`

## Korunan sözleşmeler ve doğrulama

- US-01, US-02 ve US-04–US-09. Kimlik doğrulama, rol izinleri, form alanları, doğrulama, T.C. kimlik sağlama basamakları, telefon biçimlendirme, demo hizmetleri ve rotalar korunur.
- Backend, API, veritabanı ve altyapı temel commit’ten gelir. US-09 PR #229 ve #251 için mevcut ön yüz adaptörleri korunur. Bu tasarım çalışmasında ilgili backend PR’ları alınmadı ve gerçek backend çalıştırılmadı.
- 127/127 ön yüz testi ve TypeScript/Vite üretim derlemesi geçti. Yerel sentetik tarayıcı kontrollerinde 1440, 1024, 768, 390 ve 320 px genişliklerde örnek ekranlar taşmadı. Koyu pano, Audit Logs sıralaması, masaüstünde ve tablette 35 izin kontrolü, klavye odağı, mobil doktor filtresi, 320 px kayıt doğrulaması ve yenilemeden sonra tema tercihi kontrol edildi.
- Canlı Spring Boot/PostgreSQL, Architecture sağlık isteği, tam ekran okuyucu uyumu ve sistem genelinde azaltılmış hareket emülasyonu bu turda sınanmadı.
- Geliştirme ve derleme çıktıları yalnızca ön yüzün yok sayılan `node_modules/` ve `dist/` dizinlerindedir.

[Bağımlılık envanteri](DEPENDENCIES.md)

## Yerel sentetik önizleme

Git Bash’te çalıştırın. Bu worktree için mevcut kilitli geliştirme araçları yerel olarak hazırlandı.

```bash
cd /c/Users/ardas/Projects/SoftwareEngineering321-Sprint2-Obsidian-Night/frontend

VITE_AUTH_MOCK=true \
VITE_PATIENT_REGISTRATION_MOCK=true \
VITE_PATIENT_SEARCH_MOCK=true \
VITE_PATIENT_PROFILE_MOCK=true \
VITE_STAFF_MOCK=true \
VITE_ADMIN_AUDIT_MOCK=true \
VITE_ADMIN_STAFF_MOCK=true \
VITE_STAFF_HTTP_PREVIEW=false \
VITE_DEPARTMENTS_HTTP_PREVIEW=false \
npm run dev -- --host localhost --port 5275 --strictPort
```

[Yerel giriş ekranını açın](http://localhost:5275/#/login). Sentetik yönetici `admin.demo`, resepsiyonist `reception.demo`. Demo parolası `DemoOnly!321`. Sunucuyu Ctrl+C ile durdurun.
