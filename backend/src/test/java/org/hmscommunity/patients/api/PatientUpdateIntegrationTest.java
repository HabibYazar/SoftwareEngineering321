package org.hmscommunity.patients.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.json;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.hmscommunity.patients.application.PatientService;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
import org.hmscommunity.security.audit.FieldChange;
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

/** US-08 (#197) kabul kriterlerinin HTTP katmanında doğrulanması. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class PatientUpdateIntegrationTest {

    private static final AuthenticatedStaff SEEDER =
        new AuthenticatedStaff(UUID.randomUUID(), "test.seeder", StaffRole.RECEPTIONIST);

    @Autowired MockMvc mvc;
    @Autowired InMemoryPatientRepository patients;
    @Autowired RecordingAuditEventStore auditStore;
    @Autowired PatientService patientService;

    private String receptionistToken;
    private Patient mild;

    @BeforeEach
    void setUp() throws Exception {
        patients.clear();
        mild = patientService.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails(), SEEDER);
        receptionistToken = loginAs(mvc, StaffRole.RECEPTIONIST);
        auditStore.clear();
    }

    @Test
    void permittedFieldsAreUpdatedAndAudited() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("address", "Güncel Sentetik Adres No:9, Bursa");

        update(mild.id(), body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(mild.id().toString()))
            .andExpect(jsonPath("$.address").value("Güncel Sentetik Adres No:9, Bursa"))
            .andExpect(jsonPath("$.nationalId").value(SyntheticPatients.NATIONAL_ID));

        assertThat(auditStore.events(AuditAction.UPDATE)).singleElement().satisfies(event -> {
            assertThat(event.actor().role()).isEqualTo(StaffRole.RECEPTIONIST);
            assertThat(event.changes()).containsExactly(new FieldChange("address",
                "Sentetik Mah. Test Sok. No:1, Ankara", "Güncel Sentetik Adres No:9, Bursa"));
        });
    }

    @Test
    void changingTheNationalIdIsRejected() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("nationalId", SyntheticPatients.OTHER_NATIONAL_ID);

        update(mild.id(), body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("nationalId"))
            .andExpect(jsonPath("$.errors[0].code").value("Immutable"));

        assertThat(patients.findById(mild.id())).contains(mild);
        assertThat(auditStore.events(AuditAction.UPDATE)).isEmpty();
    }

    @Test
    void resendingTheSameNationalIdIsAccepted() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("nationalId", SyntheticPatients.NATIONAL_ID);

        update(mild.id(), body).andExpect(status().isOk());
    }

    @Test
    void advancedConditionWithoutRelativeIsRejected() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("conditionLevel", "ADVANCED");

        update(mild.id(), body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("relativeInformation"));
        assertThat(patients.findById(mild.id())).contains(mild);
    }

    @Test
    void advancedConditionWithRelativeIsAccepted() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("conditionLevel", "ADVANCED");
        body.put("relativeInformation", SyntheticPatients.relativeJson());

        update(mild.id(), body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conditionLevel").value("ADVANCED"))
            .andExpect(jsonPath("$.relativeInformation.phone").value("0555 000 00 99"));
    }

    @Test
    void invalidFieldsAreRejectedWithFieldErrors() throws Exception {
        Map<String, Object> body = updateBody();
        body.put("lastName", "");
        body.put("dateOfBirth", "2999-01-01");

        update(mild.id(), body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'lastName')].code").value("NotBlank"))
            .andExpect(jsonPath("$.errors[?(@.field == 'dateOfBirth')].code").value("PastOrPresent"));
    }

    @Test
    void unknownPatientReturns404() throws Exception {
        update(UUID.randomUUID(), updateBody())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void unauthorizedRoleCannotUpdate() throws Exception {
        String adminToken = loginAs(mvc, StaffRole.ADMINISTRATOR);

        mvc.perform(put("/api/v1/patients/" + mild.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(updateBody())))
            .andExpect(status().isForbidden());
        assertThat(patients.findById(mild.id())).contains(mild);
        assertThat(auditStore.events(AuditAction.UPDATE)).isEmpty();
    }

    @Test
    void anonymousCannotUpdate() throws Exception {
        mvc.perform(put("/api/v1/patients/" + mild.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(updateBody())))
            .andExpect(status().isUnauthorized());
    }

    /** OpenAPI {@code PatientUpdate}: nationalId olmadan tüm düzenlenebilir alanlar. */
    private static Map<String, Object> updateBody() {
        Map<String, Object> body = SyntheticPatients.newPatientJson(null);
        body.remove("nationalId");
        return body;
    }

    private ResultActions update(UUID patientId, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/v1/patients/" + patientId)
            .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)));
    }
}
