package org.hmscommunity.patients.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.hmscommunity.testsupport.OpenApiContract;
import org.junit.jupiter.api.Test;

/** Hasta alanlarına ait kod kurallarının {@code api/openapi.yaml} ile aynı kaldığını doğrular. */
class PatientContractTest {

    @Test
    void conditionLevelsMatchTheOpenApiContract() {
        assertThat(Arrays.stream(ConditionLevel.values()).map(Enum::name))
            .containsExactlyElementsOf(OpenApiContract.enumOf("NewPatient", "conditionLevel"));
    }

    @Test
    void phonePatternMatchesTheOpenApiContract() {
        assertThat(OpenApiContract.schema("PhoneNumber").get("pattern")).isEqualTo(PatientDetails.PHONE_PATTERN);
    }

    @Test
    void onlyAdvancedRequiresRelativeInformation() {
        assertThat(ConditionLevel.ADVANCED.requiresRelativeInformation()).isTrue();
        assertThat(ConditionLevel.MODERATE.requiresRelativeInformation()).isFalse();
        assertThat(ConditionLevel.MILD.requiresRelativeInformation()).isFalse();
    }
}
