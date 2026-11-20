package org.hmscommunity.patients.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hmscommunity.patients.application.PatientService;
import org.hmscommunity.patients.domain.Patient;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.audit.AuditAction;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US-07 (#187) kabul kriterlerinin HTTP katmanında doğrulanması. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class PatientSearchIntegrationTest {

    private static final AuthenticatedStaff SEEDER =
        new AuthenticatedStaff(UUID.randomUUID(), "test.seeder", StaffRole.RECEPTIONIST);

    @Autowired MockMvc mvc;
    @Autowired InMemoryPatientRepository patients;
    @Autowired RecordingAuditEventStore auditStore;
    @Autowired PatientService patientService;

    private String receptionistToken;
    private Patient mild;
    private Patient advanced;

    @BeforeEach
    void setUp() throws Exception {
        patients.clear();
        mild = patientService.register(SyntheticPatients.NATIONAL_ID, SyntheticPatients.mildDetails(), SEEDER);
        advanced = patientService.register(SyntheticPatients.OTHER_NATIONAL_ID, SyntheticPatients.advancedDetails(), SEEDER);
        receptionistToken = loginAs(mvc, StaffRole.RECEPTIONIST);
        auditStore.clear();
    }

    @Test
    void exactNationalIdReturnsTheCorrectPatient() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", SyntheticPatients.OTHER_NATIONAL_ID)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(advanced.id().toString()))
            .andExpect(jsonPath("$[0].nationalId").value(SyntheticPatients.OTHER_NATIONAL_ID))
            .andExpect(jsonPath("$[0].relativeInformation.fullName").value("Deneme Yakın"));
    }

    @Test
    void partialNameReturnsMatchingPatients() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("name", "sentet")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].lastName").value("Sentetikçi"));
    }

    @Test
    void noMatchReturnsEmptyArrayNotAnError() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("name", "Bulunmayan")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", "99999999999")))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void searchWithoutCriteriaIsRejected() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("searchCriteria"));
    }

    @Test
    void searchWithBothCriteriaIsRejected() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("name", "Deneme")
                .param("nationalId", SyntheticPatients.NATIONAL_ID)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void malformedNationalIdIsRejected() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", "12345")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("nationalId"));
    }

    @Test
    void searchResultsAreAuditedAsViews() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients").param("nationalId", SyntheticPatients.NATIONAL_ID)))
            .andExpect(status().isOk());

        assertThat(auditStore.events(AuditAction.VIEW)).singleElement().satisfies(event -> {
            assertThat(event.resourceId()).isEqualTo(mild.id().toString());
            assertThat(event.actor().username()).isEqualTo("test.receptionist");
        });
    }

    @Test
    void getByIdReturnsThePatientAndAuditsTheView() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients/" + mild.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(mild.id().toString()))
            .andExpect(jsonPath("$.firstName").value("Deneme"));

        assertThat(auditStore.events(AuditAction.VIEW)).singleElement()
            .satisfies(event -> assertThat(event.details()).containsEntry("accessChannel", "RECORD"));
    }

    @Test
    void unknownPatientIdReturns404() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients/" + UUID.randomUUID())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void malformedPatientIdReturns400() throws Exception {
        mvc.perform(asReceptionist(get("/api/v1/patients/not-a-uuid")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    }

    @Test
    void unauthorizedRolesCannotSeePatientData() throws Exception {
        String physicianToken = loginAs(mvc, StaffRole.PHYSICIAN);

        String body = mvc.perform(get("/api/v1/patients").param("nationalId", SyntheticPatients.NATIONAL_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer(physicianToken)))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(SyntheticPatients.NATIONAL_ID).doesNotContain("Deneme");
        assertThat(auditStore.events(AuditAction.VIEW)).isEmpty();
    }

    @Test
    void anonymousCannotSearch() throws Exception {
        mvc.perform(get("/api/v1/patients").param("name", "Deneme"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/" + mild.id()))
            .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asReceptionist(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken));
    }
}
