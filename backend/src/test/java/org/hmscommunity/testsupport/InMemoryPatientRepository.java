package org.hmscommunity.testsupport;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.hmscommunity.patients.application.DuplicateNationalIdException;
import org.hmscommunity.patients.application.PatientNotFoundException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.Patient;

/**
 * #177 veri modeli hazır olana kadar backend davranışını doğrulamak için kullanılan test dublörü.
 * Veritabanındaki UNIQUE(national_id) kısıtını atomik {@code putIfAbsent} ile, ad aramasını ise
 * {@link PatientRepository#searchByName(String)} sözleşmesindeki anlamla (Türkçe, harf duyarsız) taklit eder.
 */
public class InMemoryPatientRepository implements PatientRepository {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final Collator TURKISH_COLLATION = Collator.getInstance(TURKISH);
    private static final Comparator<Patient> ORDER = Comparator
        .comparing(Patient::lastName, TURKISH_COLLATION)
        .thenComparing(Patient::firstName, TURKISH_COLLATION)
        .thenComparing(Patient::id);

    private final Map<UUID, Patient> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> idByNationalId = new ConcurrentHashMap<>();

    @Override
    public Patient insert(Patient patient) {
        if (idByNationalId.putIfAbsent(patient.nationalId(), patient.id()) != null) {
            throw new DuplicateNationalIdException();
        }
        byId.put(patient.id(), patient);
        return patient;
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Patient> findByNationalId(String nationalId) {
        return Optional.ofNullable(idByNationalId.get(nationalId)).map(byId::get);
    }

    @Override
    public List<Patient> searchByName(String nameFragment) {
        String needle = nameFragment.toLowerCase(TURKISH);
        return byId.values().stream()
            .filter(patient -> (patient.firstName() + " " + patient.lastName()).toLowerCase(TURKISH).contains(needle))
            .sorted(ORDER)
            .toList();
    }

    @Override
    public Patient update(Patient patient) {
        Patient existing = byId.get(patient.id());
        if (existing == null) {
            throw new PatientNotFoundException();
        }
        // Gerçek adaptörde olduğu gibi ulusal kimlik numarası sütunu güncellemeye dahil edilmez.
        Patient stored = existing.withDetails(patient.details());
        byId.put(stored.id(), stored);
        return stored;
    }

    public int count() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
        idByNationalId.clear();
    }
}
