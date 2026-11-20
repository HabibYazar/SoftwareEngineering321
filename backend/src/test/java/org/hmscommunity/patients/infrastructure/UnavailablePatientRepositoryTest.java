package org.hmscommunity.patients.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.hmscommunity.common.IntegrationPendingException;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.Test;

/** Veri modeli entegrasyonu yokken hasta kalıcılığının veri uydurmadan başarısız olduğunu doğrular. */
class UnavailablePatientRepositoryTest {

    private final UnavailablePatientRepository repository = new UnavailablePatientRepository();

    @Test
    void everyOperationFailsClosed() {
        Patient patient = Patient.create(UUID.randomUUID(), SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails());

        assertThatThrownBy(() -> repository.insert(patient)).isInstanceOf(IntegrationPendingException.class);
        assertThatThrownBy(() -> repository.findById(patient.id())).isInstanceOf(IntegrationPendingException.class);
        assertThatThrownBy(() -> repository.findByNationalId(SyntheticPatients.NATIONAL_ID))
            .isInstanceOf(IntegrationPendingException.class);
        assertThatThrownBy(() -> repository.searchByName("Deneme")).isInstanceOf(IntegrationPendingException.class);
    }
}
