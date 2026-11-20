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
import org.hmscommunity.patients.domain.RelativeInformation;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.AuditEvent;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.security.audit.FieldChange;
import org.hmscommunity.testsupport.InMemoryPatientRepository;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** US-08 (#197): denetlenen hasta güncellemeleri. */
class PatientUpdateServiceTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final AuthenticatedStaff RECEPTIONIST =
        new AuthenticatedStaff(UUID.randomUUID(), "test.receptionist", StaffRole.RECEPTIONIST);

    private final InMemoryPatientRepository repository = new InMemoryPatientRepository();
    private final RecordingAuditEventStore auditStore = new RecordingAuditEventStore();
    private final PatientService service = new PatientService(repository,
        new PatientValidator(FACTORY.getValidator()), new AuditService(auditStore, Clock.systemUTC()));

    private Patient mild;

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @BeforeEach
    void seed() {
        mild = service.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails(), RECEPTIONIST);
        auditStore.clear();
    }

    @Test
    void allFieldsExceptNationalIdCanBeUpdated() {
        PatientDetails changed = new PatientDetails("Yeni", "Soyad", LocalDate.of(1991, 6, 18), "Other",
            "0555 000 00 55", "Yeni Sentetik Adres No:5", ConditionLevel.MODERATE, SyntheticPatients.relative());

        Patient updated = service.update(mild.id(), null, changed, RECEPTIONIST);

        assertThat(updated.details()).isEqualTo(changed);
        assertThat(updated.id()).isEqualTo(mild.id());
        assertThat(updated.nationalId()).isEqualTo(SyntheticPatients.NATIONAL_ID);
        assertThat(repository.findById(mild.id())).contains(updated);
    }

    @Test
    void successfulUpdateIsAuditedWithBeforeAndAfterValues() {
        PatientDetails changed = withPhone(mild.details(), "0555 000 00 77");

        service.update(mild.id(), null, changed, RECEPTIONIST);

        AuditEvent event = auditStore.events(AuditAction.UPDATE).getFirst();
        assertThat(event.actor()).isEqualTo(RECEPTIONIST);
        assertThat(event.resourceId()).isEqualTo(mild.id().toString());
        assertThat(event.changes()).containsExactly(new FieldChange("phone", "0555 000 00 01", "0555 000 00 77"));
    }

    @Test
    void auditNeverContainsTheNationalId() {
        service.update(mild.id(), SyntheticPatients.NATIONAL_ID, withPhone(mild.details(), "0555 000 00 78"), RECEPTIONIST);

        assertThat(auditStore.events().getFirst().toString()).doesNotContain(SyntheticPatients.NATIONAL_ID);
    }

    @Test
    void addingRelativeRecordsEachRelativeField() {
        PatientDetails advanced = withLevelAndRelative(mild.details(), ConditionLevel.ADVANCED, SyntheticPatients.relative());

        service.update(mild.id(), null, advanced, RECEPTIONIST);

        assertThat(auditStore.events().getFirst().changes()).containsExactly(
            new FieldChange("conditionLevel", "MILD", "ADVANCED"),
            new FieldChange("relativeInformation.fullName", null, "Deneme Yakın"),
            new FieldChange("relativeInformation.relationship", null, "Sibling"),
            new FieldChange("relativeInformation.phone", null, "0555 000 00 99"));
    }

    @Test
    void changingTheNationalIdIsRejected() {
        assertThatThrownBy(() -> service.update(mild.id(), SyntheticPatients.OTHER_NATIONAL_ID,
                withPhone(mild.details(), "0555 000 00 66"), RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("nationalId", "Immutable")));

        assertThat(repository.findById(mild.id())).contains(mild);
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void sendingTheUnchangedNationalIdIsAllowed() {
        Patient updated = service.update(mild.id(), SyntheticPatients.NATIONAL_ID,
            withPhone(mild.details(), "0555 000 00 66"), RECEPTIONIST);

        assertThat(updated.phone()).isEqualTo("0555 000 00 66");
    }

    @Test
    void raisingToAdvancedWithoutRelativeIsRejected() {
        PatientDetails advancedWithoutRelative = withLevelAndRelative(mild.details(), ConditionLevel.ADVANCED, null);

        assertThatThrownBy(() -> service.update(mild.id(), null, advancedWithoutRelative, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::code).containsExactly("RequiredForAdvancedCondition"));

        assertThat(repository.findById(mild.id())).contains(mild);
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void removingRelativeFromAdvancedPatientIsRejected() {
        Patient advanced = service.register(SyntheticPatients.OTHER_NATIONAL_ID, SyntheticPatients.advancedDetails(), RECEPTIONIST);
        PatientDetails withoutRelative = withLevelAndRelative(advanced.details(), ConditionLevel.ADVANCED, null);

        assertThatThrownBy(() -> service.update(advanced.id(), null, withoutRelative, RECEPTIONIST))
            .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void us06ValidationRulesApplyToUpdates() {
        PatientDetails invalid = new PatientDetails(" ", mild.lastName(), LocalDate.now().plusDays(1), mild.gender(),
            "abc", mild.address(), mild.conditionLevel(), new RelativeInformation("Ad", "", "0555 000 00 00"));

        assertThatThrownBy(() -> service.update(mild.id(), null, invalid, RECEPTIONIST))
            .isInstanceOfSatisfying(RequestValidationException.class, ex ->
                assertThat(ex.violations()).extracting(FieldViolation::field).containsExactlyInAnyOrder(
                    "firstName", "dateOfBirth", "phone", "relativeInformation.relationship"));
    }

    @Test
    void unknownPatientIs404() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), null, mild.details(), RECEPTIONIST))
            .isInstanceOf(PatientNotFoundException.class);
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void identicalUpdateIsStillAuditedWithNoChanges() {
        service.update(mild.id(), null, mild.details(), RECEPTIONIST);

        assertThat(auditStore.events(AuditAction.UPDATE)).singleElement()
            .satisfies(event -> assertThat(event.changes()).isEmpty());
    }

    @Test
    void recordableFieldsMatchTheEditableProfile() {
        assertThat(PatientChangeTracking.snapshot(mild).keySet())
            .containsExactlyInAnyOrderElementsOf(PatientChangeTracking.RECORDABLE_FIELDS)
            .doesNotContain("nationalId");
    }

    private static PatientDetails withPhone(PatientDetails details, String phone) {
        return new PatientDetails(details.firstName(), details.lastName(), details.dateOfBirth(), details.gender(),
            phone, details.address(), details.conditionLevel(), details.relativeInformation());
    }

    private static PatientDetails withLevelAndRelative(PatientDetails details, ConditionLevel level,
            RelativeInformation relative) {
        return new PatientDetails(details.firstName(), details.lastName(), details.dateOfBirth(), details.gender(),
            details.phone(), details.address(), level, relative);
    }
}
