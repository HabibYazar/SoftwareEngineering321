package org.hmscommunity.patients.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.patients.domain.Patient;

/**
 * Hasta kalıcılığı için backend tarafındaki entegrasyon arayüzü (port).
 *
 * <p>Kalıcı uygulama ve şema #177 ("Model patient identity, condition and emergency contacts") ve
 * #196 ("Support permitted patient profile updates") veri iş akışı tarafından sağlanmalıdır.
 * Beklenen sözleşme:
 * <ul>
 *   <li>{@code national_id} sütununda veritabanı düzeyinde UNIQUE kısıtı bulunur; eşzamanlı iki kayıt
 *       isteğinde ihlal {@link DuplicateNationalIdException} olarak çevrilir.</li>
 *   <li>Hasta kimliği ({@link Patient#id()}) backend tarafından UUID olarak üretilir ve aynen saklanır.</li>
 *   <li>Yakın bilgisi (relative) isteğe bağlıdır; verilmişse üç alanı birlikte saklanır.</li>
 * </ul>
 * Uygulama bean olarak eklendiğinde geçici {@code UnavailablePatientRepository} otomatik olarak devre dışı kalır.
 */
public interface PatientRepository {

    /** Yeni hastayı ekler; ulusal kimlik numarası zaten kayıtlıysa {@link DuplicateNationalIdException} fırlatır. */
    Patient insert(Patient patient);

    Optional<Patient> findById(UUID id);

    /** Ulusal kimlik numarasına göre birebir (exact) eşleşme. */
    Optional<Patient> findByNationalId(String nationalId);

    /**
     * US-07 kısmi ad araması. Beklenen anlam: {@code nameFragment}, Türkçe yerel ayarına göre büyük/küçük
     * harf duyarsız olarak ad, soyad veya "ad soyad" birleşiminin herhangi bir yerinde geçiyorsa eşleşir
     * (ör. PostgreSQL'de tr-TR ICU harmanlamasıyla {@code ILIKE '%' || :fragment || '%'}).
     * Sonuçlar Türkçe harmanlamaya göre soyad, ad ve ardından kimliğe göre sıralanır; eşleşme yoksa boş liste döner.
     *
     * @param nameFragment servis tarafından kırpılmış, iç boşlukları teke indirilmiş ve boş olmayan değer
     */
    List<Patient> searchByName(String nameFragment);
}
