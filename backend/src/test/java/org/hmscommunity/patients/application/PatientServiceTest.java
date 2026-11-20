package org.hmscommunity.patients.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.AuditResourceType;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.testsupport.InMemoryPatientRepository;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class PatientServiceTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final AuthenticatedStaff RECEPTIONIST =
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST);

    private final InMemoryPatientRepository repository = new InMemoryPatientRepository();
    private final RecordingAuditEventStore auditStore = new RecordingAuditEventStore();
    private final PatientService service = new PatientService(repository,
        new PatientValidator(FACTORY.getValidator()), new AuditService(auditStore, Clock.systemUTC()));

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    // --- US-06 (#179): kayıt ---

    @Test
    void validPatientIsRegisteredOnceAndAudited() {
        Patient patient = service.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.advancedDetails(), RECEPTIONIST);

        assertThat(patient.id()).isNotNull();
        assertThat(patient.nationalId()).isEqualTo(SyntheticPatients.NATIONAL_ID);
        assertThat(patient.relativeInformation()).isEqualTo(SyntheticPatients.relative());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(auditStore.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.CREATE);
            assertThat(event.resourceType()).isEqualTo(AuditResourceType.PATIENT);
            assertThat(event.resourceId()).isEqualTo(patient.id().toString());
            assertThat(event.actor()).isEqualTo(RECEPTIONIST);
            assertThat(event.changes()).isEmpty();
        });
    }

    @Test
    void duplicateNationalIdIsRejectedAndNotAudited() {
        service.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails(), RECEPTIONIST);
        auditStore.clear();

        assertThatThrownBy(() -> service.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.advancedDetails(), RECEPTIONIST))
            .isInstanceOf(DuplicateNationalIdException.class);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void invalidRequestIsRejectedWithAllFieldErrorsAndNothingIsStored() {
        var mild = SyntheticPatients.mildDetails();
        var invalid = new org.hmscommunity.patients.domain.PatientDetails(" ", mild.lastName(), mild.dateOfBirth(),
            mild.gender(), mild.phone(), mild.address(), org.hmscommunity.patients.domain.ConditionLevel.ADVANCED, null);

        assertThatThrownBy(() -> service.register("123", invalid, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::field)
                    .containsExactlyInAnyOrder("nationalId", "firstName", "relativeInformation"));
        assertThat(repository.count()).isZero();
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void concurrentRegistrationsWithTheSameNationalIdProduceExactlyOnePatient() throws Exception {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    service.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails(), RECEPTIONIST);
                    return true;
                } catch (DuplicateNationalIdException duplicate) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = tasks.stream().map(pool::submit).toList();
        start.countDown();

        long successes = 0;
        for (Future<Boolean> result : results) {
            successes += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(auditStore.events(AuditAction.CREATE)).hasSize(1);
    }
}
