package org.hmscommunity.patients.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.hmscommunity.patients.application.PatientRepository;
import org.hmscommunity.patients.domain.Patient;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * #177/#196 hasta veri modeli entegre edilene kadar kullanılan geçici (fail-closed) uygulama.
 * Veri saklamaz ve uydurmaz; her çağrı HTTP 503 ile sonuçlanır. Gerçek bir {@link PatientRepository}
 * bean'i eklendiğinde {@link Fallback} sayesinde kullanılmaz.
 */
@Component
@Fallback
class UnavailablePatientRepository implements PatientRepository {

    private static final String INTEGRATION = "patient records (#177, #196)";

    @Override
    public Patient insert(Patient patient) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public Optional<Patient> findByNationalId(String nationalId) {
        throw new IntegrationPendingException(INTEGRATION);
    }

    @Override
    public List<Patient> searchByName(String nameFragment) {
        throw new IntegrationPendingException(INTEGRATION);
    }
}
