package org.hmscommunity.patients.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.json;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.InMemoryPatientRepository;
import org.hmscommunity.testsupport.RecordingAuditEventStore;
import org.hmscommunity.testsupport.SyntheticPatients;
import org.hmscommunity.testsupport.TestDoublesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** US-06 (#179) kabul kriterlerinin HTTP katmanında doğrulanması. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class PatientRegistrationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired InMemoryPatientRepository patients;
    @Autowired RecordingAuditEventStore auditStore;

    private String receptionistToken;

    @BeforeEach
    void setUp() throws Exception {
        patients.clear();
        receptionistToken = loginAs(mvc, StaffRole.RECEPTIONIST);
        auditStore.clear();
    }

    @Test
    void validMildPatientIsCreated() throws Exception {
        register(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(
                ".*/api/v1/patients/[0-9a-f-]{36}$")))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.nationalId").value(SyntheticPatients.NATIONAL_ID))
            .andExpect(jsonPath("$.firstName").value("Deneme"))
            .andExpect(jsonPath("$.dateOfBirth").value("1990-05-17"))
            .andExpect(jsonPath("$.conditionLevel").value("MILD"))
            .andExpect(jsonPath("$.relativeInformation").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());

        assertThat(patients.count()).isEqualTo(1);
        // Oluşturma kaydını veritabanı tetikleyicisi yazar (PostgresPatientPersistenceTest); Java ayrıca üretmez.
        assertThat(auditStore.events()).isEmpty();
    }

    @Test
    void advancedPatientWithRelativeIsCreated() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID);
        body.put("conditionLevel", "ADVANCED");
        body.put("relativeInformation", SyntheticPatients.relativeJson());

        register(body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.relativeInformation.fullName").value("Deneme Yakın"))
            .andExpect(jsonPath("$.relativeInformation.relationship").value("Sibling"));
    }

    @Test
    void advancedPatientWithoutRelativeIsRejected() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID);
        body.put("conditionLevel", "ADVANCED");

        register(body)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("relativeInformation"))
            .andExpect(jsonPath("$.errors[0].code").value("RequiredForAdvancedCondition"));
        assertThat(patients.count()).isZero();
    }

    @Test
    void missingAndInvalidFieldsAreReportedTogether() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson("1234");
        body.remove("firstName");
        body.put("phone", "not-a-phone");
        body.put("address", " ");

        register(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(4))
            .andExpect(jsonPath("$.errors[?(@.field == 'nationalId')].code").value("Pattern"))
            .andExpect(jsonPath("$.errors[?(@.field == 'firstName')].code").value("NotBlank"))
            .andExpect(jsonPath("$.errors[?(@.field == 'phone')].code").value("Pattern"))
            .andExpect(jsonPath("$.errors[?(@.field == 'address')].code").value("NotBlank"));
    }

    @Test
    void errorsNeverEchoRejectedValues() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson("12345abcde9");

        String response = register(body).andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("12345abcde9");
    }

    @Test
    void unknownConditionLevelIsAFieldError() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID);
        body.put("conditionLevel", "SEVERE");

        register(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("conditionLevel"))
            .andExpect(jsonPath("$.errors[0].message").value("must be one of [MILD, MODERATE, ADVANCED]"));
    }

    @Test
    void malformedDateOfBirthIsAFieldError() throws Exception {
        Map<String, Object> body = SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID);
        body.put("dateOfBirth", "17/05/1990");

        register(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("dateOfBirth"))
            .andExpect(jsonPath("$.errors[0].code").value("InvalidFormat"));
    }

    @Test
    void duplicateNationalIdReturns409() throws Exception {
        register(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID)).andExpect(status().isCreated());

        register(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_NATIONAL_ID"));
        assertThat(patients.count()).isEqualTo(1);
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeUniquenessCheck() throws Exception {
        register(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID)).andExpect(status().isCreated());

        register(SyntheticPatients.newPatientJson(" " + SyntheticPatients.NATIONAL_ID + " "))
            .andExpect(status().isConflict());
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void nonReceptionistCannotRegister() throws Exception {
        String nurseToken = loginAs(mvc, StaffRole.NURSE);

        mvc.perform(post("/api/v1/patients")
                .header(HttpHeaders.AUTHORIZATION, bearer(nurseToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID))))
            .andExpect(status().isForbidden());
        assertThat(patients.count()).isZero();
    }

    @Test
    void anonymousCannotRegister() throws Exception {
        mvc.perform(post("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(SyntheticPatients.newPatientJson(SyntheticPatients.NATIONAL_ID))))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, endsWith("Bearer")));
        assertThat(patients.count()).isZero();
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/patients")
            .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)));
    }
}
