package org.hmscommunity.patients.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.security.audit.AuditService.AccessChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hasta yönetimi iş kuralları. Yetkilendirme (US-02) güvenlik filtre zincirinde ve veritabanında,
 * doğrulama burada uygulanır.
 *
 * <p>Denetim (US-03): oluşturma ve güncelleme kayıtları (önceki/sonraki değerlerle) veritabanı
 * tetikleyicileri tarafından, görüntüleme kayıtları bu serviste üretilir. Her metot tek bir veritabanı
 * işleminde (transaction) çalışır; iş verisi ile ona ait denetim kaydı birlikte kalıcı olur ya da
 * birlikte geri alınır.
 */
@Service
@Transactional
public class PatientService {

    /** OpenAPI {@code searchPatients.name} için üst sınır. */
    static final int MAX_NAME_QUERY_LENGTH = 150;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final PatientRepository patients;
    private final PatientValidator validator;
    private final AuditService audit;

    public PatientService(PatientRepository patients, PatientValidator validator, AuditService audit) {
        this.patients = patients;
        this.validator = validator;
        this.audit = audit;
    }

    /**
     * US-06 (#179): yeni hasta kaydı. Ulusal kimlik numarası benzersiz olmalıdır; benzersizlik hem
     * burada hem de (eşzamanlı istekler için) veritabanındaki UNIQUE kısıtı ile korunur.
     */
    public Patient register(String nationalId, PatientDetails details, AuthenticatedStaff actor) {
        List<FieldViolation> violations = new ArrayList<>(validator.validateNationalId(nationalId));
        violations.addAll(validator.validateDetails(details));
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        if (patients.findByNationalId(nationalId).isPresent()) {
            throw new DuplicateNationalIdException();
        }
        return patients.insert(Patient.create(UUID.randomUUID(), nationalId, details), actor);
    }

    /**
     * US-07 (#187): ulusal kimlik numarasıyla birebir veya adla kısmi arama. Ölçütlerden tam olarak biri
     * verilmelidir; tüm hastaların ölçütsüz listelenmesine izin verilmez. Eşleşme yoksa boş liste döner.
     * Sonuçta gösterilen her hasta için ayrı bir görüntüleme denetim kaydı üretilir (US-03).
     */
    public List<Patient> search(String name, String nationalId, AuthenticatedStaff actor) {
        boolean hasName = name != null && !name.isBlank();
        boolean hasNationalId = nationalId != null && !nationalId.isBlank();
        if (hasName == hasNationalId) {
            throw new RequestValidationException(List.of(new FieldViolation("searchCriteria", "ExactlyOneRequired",
                "provide exactly one of name or nationalId")));
        }
        List<Patient> results;
        if (hasNationalId) {
            String normalizedNationalId = nationalId.strip();
            List<FieldViolation> violations = validator.validateNationalId(normalizedNationalId);
            if (!violations.isEmpty()) {
                throw new RequestValidationException(violations);
            }
            results = patients.findByNationalId(normalizedNationalId).map(List::of).orElse(List.of());
        } else {
            String fragment = WHITESPACE.matcher(name.strip()).replaceAll(" ");
            if (fragment.length() > MAX_NAME_QUERY_LENGTH) {
                throw new RequestValidationException(List.of(new FieldViolation("name", "Size",
                    "size must be between 1 and " + MAX_NAME_QUERY_LENGTH)));
            }
            results = patients.searchByName(fragment);
        }
        results.forEach(patient -> audit.patientRecordViewed(actor, patient.id(), AccessChannel.SEARCH_RESULT));
        return results;
    }

    /** US-07/US-08: tek hasta kaydını görüntüleme; görüntüleme denetlenir (US-03). */
    public Patient get(UUID patientId, AuthenticatedStaff actor) {
        Patient patient = patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
        audit.patientRecordViewed(actor, patient.id(), AccessChannel.RECORD);
        return patient;
    }

    /**
     * US-08 (#197): ulusal kimlik numarası dışındaki tüm alanların güncellenmesi (tam değiştirme).
     *
     * <p>US-06 doğrulama kuralları aynen uygulanır; seviye "Advanced" yapılırsa yakın bilgisi zorunludur.
     * İstekte ulusal kimlik numarası gönderilirse mevcut değerle aynı olmalıdır, farklıysa reddedilir.
     * Bu kural Administrator için de geçerlidir: veritabanı yöneticinin ulusal kimlik numarasını
     * düzeltmesine izin verse de US-08 numaranın güncellenemeyeceğini söyler.
     */
    public Patient update(UUID patientId, String requestedNationalId, PatientDetails details,
            AuthenticatedStaff actor) {
        Patient current = patients.findById(patientId).orElseThrow(PatientNotFoundException::new);
        List<FieldViolation> violations = new ArrayList<>();
        if (requestedNationalId != null && !requestedNationalId.equals(current.nationalId())) {
            violations.add(new FieldViolation("nationalId", "Immutable", "cannot be changed"));
        }
        violations.addAll(validator.validateDetails(details));
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return patients.update(current.withDetails(details), actor);
    }
}
