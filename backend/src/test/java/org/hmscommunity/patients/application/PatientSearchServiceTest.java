package org.hmscommunity.patients.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.AuditEvent;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.testsupport.InMemoryPatientRepository;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-07 (#187): hasta arama ve tek kayıt görüntüleme iş kuralları. */
class PatientSearchServiceTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final AuthenticatedStaff RECEPTIONIST =
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST);

    private final InMemoryPatientRepository repository = new InMemoryPatientRepository();
    private final RecordingAuditEventStore auditStore = new RecordingAuditEventStore();
    private final PatientService service = new PatientService(repository,
        new PatientValidator(FACTORY.getValidator()), new AuditService(auditStore, Clock.systemUTC()));

    private Patient ayse;
    private Patient ileri;
    private Patient isik;

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @BeforeEach
    void seed() {
        ayse = service.register("90000000011", details("Ayşe", "Deneme"), RECEPTIONIST);
        ileri = service.register("90000000012", details("İleri", "Sentetikçi"), RECEPTIONIST);
        isik = service.register("90000000013", details("Işık", "Örnekoğlu"), RECEPTIONIST);
        auditStore.clear();
    }

    @Test
    void exactNationalIdReturnsTheMatchingPatient() {
        assertThat(service.search(null, "90000000012", RECEPTIONIST)).containsExactly(ileri);
    }

    @Test
    void nationalIdSearchIsExactNotPartial() {
        assertThat(service.search(null, "90000000010", RECEPTIONIST)).isEmpty();
    }

    @Test
    void partialNameMatchesFirstOrLastNameIgnoringCase() {
        assertThat(service.search("deneme", null, RECEPTIONIST)).containsExactly(ayse);
        assertThat(service.search("SENTET", null, RECEPTIONIST)).containsExactly(ileri);
    }

    @Test
    void partialNameMatchesFullNameAndCollapsesWhitespace() {
        assertThat(service.search("  ayşe    den ", null, RECEPTIONIST)).containsExactly(ayse);
    }

    @Test
    void turkishDottedAndDotlessICaseFoldingIsRespected() {
        assertThat(service.search("ileri", null, RECEPTIONIST)).containsExactly(ileri);
        assertThat(service.search("IŞIK", null, RECEPTIONIST)).containsExactly(isik);
    }

    @Test
    void partialNameReturnsAllMatchesInPredictableOrder() {
        // "e" üç hastanın da adında/soyadında geçer; sıralama soyada göredir.
        assertThat(service.search("e", null, RECEPTIONIST)).containsExactly(ayse, isik, ileri);
    }

    @Test
    void noMatchReturnsEmptyListAndNoViewEvents() {
        assertThat(service.search("Bulunmayan", null, RECEPTIONIST)).isEmpty();
        assertThat(service.search(null, "99999999999", RECEPTIONIST)).isEmpty();
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void everyDisclosedPatientIsAuditedAsViewed() {
        service.search("e", null, RECEPTIONIST);

        assertThat(auditStore.events(AuditAction.VIEW))
            .extracting(AuditEvent::resourceId)
            .containsExactly(ayse.id().toString(), isik.id().toString(), ileri.id().toString());
        assertThat(auditStore.events()).allSatisfy(event -> {
            assertThat(event.details()).containsEntry("accessChannel", "SEARCH_RESULT");
            assertThat(event.actor()).isEqualTo(RECEPTIONIST);
            assertThat(event.toString()).doesNotContain("Ayşe").doesNotContain("90000000011");
        });
    }

    @Test
    void neitherCriterionIsRejected() {
        assertThatThrownBy(() -> service.search(" ", null, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::code).containsExactly("ExactlyOneRequired"));
    }

    @Test
    void bothCriteriaAreRejected() {
        assertThatThrownBy(() -> service.search("Ayşe", "90000000011", RECEPTIONIST))
            .isInstanceOf(RequestValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9000000001", "900000000111", "9000000001a"})
    void malformedNationalIdIsRejected(String nationalId) {
        assertThatThrownBy(() -> service.search(null, nationalId, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::field).containsExactly("nationalId"));
    }

    @Test
    void overlongNameIsRejected() {
        assertThatThrownBy(() -> service.search("a".repeat(151), null, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::field).containsExactly("name"));
    }

    @Test
    void getReturnsPatientAndAuditsTheView() {
        assertThat(service.get(ayse.id(), RECEPTIONIST)).isEqualTo(ayse);

        assertThat(auditStore.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.VIEW);
            assertThat(event.resourceId()).isEqualTo(ayse.id().toString());
            assertThat(event.details()).containsEntry("accessChannel", "RECORD");
        });
    }

    @Test
    void getUnknownPatientIs404AndNotAudited() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), RECEPTIONIST))
            .isInstanceOf(PatientNotFoundException.class);
        assertThat(auditStore.events()).isEmpty();
    }

    private static PatientDetails details(String firstName, String lastName) {
        PatientDetails base = SyntheticPatients.mildDetails();
        return new PatientDetails(firstName, lastName, LocalDate.of(1980, 3, 3), base.gender(), base.phone(),
            base.address(), ConditionLevel.MODERATE, null);
    }
}
