package org.hmscommunity.testsupport;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.hmscommunity.patients.application.DuplicateNationalIdException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.Patient;

/**
 * #177 veri modeli hazır olana kadar backend davranışını doğrulamak için kullanılan test dublörü.
 * Veritabanındaki UNIQUE(national_id) kısıtını atomik {@code putIfAbsent} ile taklit eder.
 */
public class InMemoryPatientRepository implements PatientRepository {

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
    public Optional<Patient> findByNationalId(String nationalId) {
        return Optional.ofNullable(idByNationalId.get(nationalId)).map(byId::get);
    }

    public int count() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
        idByNationalId.clear();
    }
}
