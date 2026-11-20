package org.hmscommunity.security.audit;

import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.TestDoublesConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #145: hiçbir rol (Administrator dahil) denetim kayıtlarını HTTP üzerinden değiştiremez veya silemez.
 * Denetim kaydı okuma/arama API'si (US-04, #154) bu sprintin kapsamı dışındadır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class AuditApiExposureIntegrationTest {

    private static final String AUDIT_EVENT = "/api/v1/audit-events/6f1c2b9e-3d4a-4f5b-8c6d-7e8f9a0b1c2d";

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @EnumSource(StaffRole.class)
    void auditRecordsCannotBeModifiedOrDeleted(StaffRole role) throws Exception {
        String token = loginAs(mvc, role);

        mvc.perform(put(AUDIT_EVENT).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
        mvc.perform(patch(AUDIT_EVENT).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
        mvc.perform(delete(AUDIT_EVENT).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden());
    }
}
