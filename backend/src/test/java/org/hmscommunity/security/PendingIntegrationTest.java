package org.hmscommunity.security;

import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test dublörleri olmadan, yalnızca üretim bean'leriyle çalışan bağlam.
 * Veri modeli entegrasyonu eksikken sistemin güvenli tarafta kaldığını (token vermediğini) doğrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PendingIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void loginFailsClosedUntilStaffPersistenceIsIntegrated() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("any.user", "any-password")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INTEGRATION_PENDING"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
