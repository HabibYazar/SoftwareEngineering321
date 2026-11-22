package org.hmscommunity.patients.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.patients.domain.ConditionLevel;
import org.hmscommunity.patients.domain.PatientDetails;
import org.hmscommunity.patients.domain.RelativeInformation;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** US-06 doğrulama kuralları (US-08 güncellemelerinde de aynen geçerlidir). */
class PatientValidatorTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final PatientValidator validator = new PatientValidator(FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void validDetailsHaveNoViolations() {
        assertThat(validator.validateDetails(SyntheticPatients.mildDetails())).isEmpty();
        assertThat(validator.validateDetails(SyntheticPatients.advancedDetails())).isEmpty();
    }

    @Test
    void everyMissingRequiredFieldIsReportedAtOnce() {
        PatientDetails empty = new PatientDetails(null, " ", null, "", null, null, null, null);

        assertThat(fields(validator.validateDetails(empty))).containsExactlyInAnyOrder(
            "firstName", "lastName", "dateOfBirth", "gender", "phone", "address", "conditionLevel");
    }

    @Test
    void violationsUseConstraintNamesAsCodes() {
        PatientDetails details = withFirstName(" ");

        assertThat(validator.validateDetails(details))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("firstName", "NotBlank"));
    }

    @Test
    void futureDateOfBirthIsRejected() {
        PatientDetails mild = SyntheticPatients.mildDetails();
        PatientDetails future = new PatientDetails(mild.firstName(), mild.lastName(), LocalDate.now().plusDays(2),
            mild.gender(), mild.phone(), mild.address(), mild.conditionLevel(), null);

        assertThat(validator.validateDetails(future))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("dateOfBirth", "PastOrPresent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12", "0555-ABC-0000", "+", "05550000001-"})
    void malformedPhoneIsRejected(String phone) {
        PatientDetails mild = SyntheticPatients.mildDetails();
        PatientDetails details = new PatientDetails(mild.firstName(), mild.lastName(), mild.dateOfBirth(),
            mild.gender(), phone, mild.address(), mild.conditionLevel(), null);

        assertThat(fields(validator.validateDetails(details))).containsExactly("phone");
    }

    @Test
    void tooLongValuesAreRejected() {
        PatientDetails details = withFirstName("x".repeat(101));

        assertThat(validator.validateDetails(details)).extracting(FieldViolation::code).containsExactly("Size");
    }

    @Test
    void advancedConditionRequiresRelativeInformation() {
        PatientDetails details = withLevelAndRelative(ConditionLevel.ADVANCED, null);

        assertThat(validator.validateDetails(details))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("relativeInformation", "RequiredForAdvancedCondition"));
    }

    @Test
    void advancedConditionRequiresAllRelativeFields() {
        PatientDetails details = withLevelAndRelative(ConditionLevel.ADVANCED, new RelativeInformation("Ad Soyad", " ", null));

        assertThat(fields(validator.validateDetails(details)))
            .containsExactlyInAnyOrder("relativeInformation.relationship", "relativeInformation.phone");
    }

    @ParameterizedTest
    @EnumSource(value = ConditionLevel.class, names = {"MILD", "MODERATE"})
    void relativeInformationIsOptionalForMildAndModerate(ConditionLevel level) {
        assertThat(validator.validateDetails(withLevelAndRelative(level, null))).isEmpty();
    }

    @Test
    void optionalRelativeMustStillBeCompleteWhenProvided() {
        PatientDetails details = withLevelAndRelative(ConditionLevel.MILD, new RelativeInformation("Ad Soyad", "Parent", "abc"));

        assertThat(fields(validator.validateDetails(details))).containsExactly("relativeInformation.phone");
    }

    @Test
    void wellFormedNationalIdIsAccepted() {
        assertThat(validator.validateNationalId(SyntheticPatients.NATIONAL_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123456789012", "1234567890a", "12345 67890", "١٢٣٤٥٦٧٨٩٠١"})
    void nationalIdMustBeExactlyElevenAsciiDigits(String nationalId) {
        assertThat(validator.validateNationalId(nationalId))
            .extracting(FieldViolation::field, FieldViolation::code)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("nationalId", "Pattern"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nationalIdIsRequired(String nationalId) {
        assertThat(validator.validateNationalId(nationalId)).extracting(FieldViolation::code).containsExactly("NotBlank");
    }

    private static List<String> fields(List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::field).toList();
    }

    private static PatientDetails withFirstName(String firstName) {
        PatientDetails mild = SyntheticPatients.mildDetails();
        return new PatientDetails(firstName, mild.lastName(), mild.dateOfBirth(), mild.gender(), mild.phone(),
            mild.address(), mild.conditionLevel(), null);
    }

    private static PatientDetails withLevelAndRelative(ConditionLevel level, RelativeInformation relative) {
        PatientDetails mild = SyntheticPatients.mildDetails();
        return new PatientDetails(mild.firstName(), mild.lastName(), mild.dateOfBirth(), mild.gender(), mild.phone(),
            mild.address(), level, relative);
    }
}
