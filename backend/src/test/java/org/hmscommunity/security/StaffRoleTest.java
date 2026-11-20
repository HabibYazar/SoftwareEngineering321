package org.hmscommunity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.hmscommunity.testsupport.OpenApiContract;
import org.junit.jupiter.api.Test;

class StaffRoleTest {

    @Test
    void allSevenUserStoryRolesAreDefined() {
        assertThat(StaffRole.values()).extracting(StaffRole::apiName).containsExactly(
            "Administrator", "Receptionist", "Physician", "Nurse",
            "PatientCareAssistant", "LabTechnician", "CleaningStaff");
    }

    @Test
    void jsonRoleNamesMatchTheOpenApiContract() {
        assertThat(Arrays.stream(StaffRole.values()).map(StaffRole::apiName))
            .containsExactlyInAnyOrderElementsOf(OpenApiContract.enumOf("LoginResponse", "role"));
    }

    @Test
    void authoritiesUseSpringRolePrefix() {
        assertThat(StaffRole.PATIENT_CARE_ASSISTANT.authority()).isEqualTo("ROLE_PATIENT_CARE_ASSISTANT");
    }
}
