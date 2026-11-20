package org.hmscommunity.patients.application;

import java.util.Optional;
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

    /** Ulusal kimlik numarasına göre birebir (exact) eşleşme. */
    Optional<Patient> findByNationalId(String nationalId);
}
