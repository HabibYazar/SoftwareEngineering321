package org.hmscommunity.patients.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;

/**
 * Hasta kalıcılığı için port; üretim uygulaması {@code JdbcPatientRepository}'dir ({@code patients},
 * {@code patient_relatives}; V3 migration'ı).
 *
 * <p>Sözleşme:
 * <ul>
 *   <li>Yazan metotlar işlemi yapan personeli alır ve veritabanı aktör bağlamını aynı işlemde kurar;
 *       veritabanının izin kontrolleri ({@code HA403}) böylece atlanamaz.</li>
 *   <li>Oluşturma ve güncellemenin denetim kayıtları (güncellemede önceki/sonraki değerler) şemadaki
 *       tetikleyiciler tarafından yazmayla aynı işlemde üretilir; çağıranın ayrıca kaydetmesi gerekmez.</li>
 *   <li>{@code national_id} benzersizdir; eşzamanlı iki kayıtta ihlal {@link DuplicateNationalIdException}
 *       olarak çevrilir.</li>
 *   <li>Yakın bilgisi (relative) isteğe bağlıdır; verilmişse üç alanı birlikte saklanır, verilmemişse kayıt yoktur.</li>
 * </ul>
 * Veritabanı yapılandırılmamışsa geçici {@code UnavailablePatientRepository} kullanılır (HTTP 503).
 */
public interface PatientRepository {

    /** Yeni hastayı ekler; ulusal kimlik numarası zaten kayıtlıysa {@link DuplicateNationalIdException} fırlatır. */
    Patient insert(Patient patient, AuthenticatedStaff actor);

    Optional<Patient> findById(UUID id);

    /** Ulusal kimlik numarasına göre birebir (exact) eşleşme. */
    Optional<Patient> findByNationalId(String nationalId);

    /**
     * US-07 kısmi ad araması: {@code nameFragment}, Türkçe büyük/küçük harf kurallarına göre (İ/i, I/ı)
     * harf duyarsız olarak "ad soyad" birleşiminin herhangi bir yerinde geçiyorsa eşleşir.
     * Sonuçlar Türkçe harmanlamaya göre soyad, ad ve ardından kimliğe göre (belirlenimci) sıralanır;
     * eşleşme yoksa boş liste döner.
     *
     * @param nameFragment servis tarafından kırpılmış, iç boşlukları teke indirilmiş ve boş olmayan değer
     */
    List<Patient> searchByName(String nameFragment);

    /**
     * US-08: aynı kimlikteki hastanın ulusal kimlik numarası DIŞINDAKİ tüm alanlarını günceller.
     * Uygulama {@code national_id} sütununu asla güncellemez; kayıt artık yoksa
     * {@link PatientNotFoundException} fırlatılır.
     */
    Patient update(Patient patient, AuthenticatedStaff actor);
}
