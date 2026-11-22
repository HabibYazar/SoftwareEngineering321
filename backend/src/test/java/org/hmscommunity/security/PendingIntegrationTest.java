package org.hmscommunity.security;

import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test dublörleri olmadan, yalnızca üretim bean'leriyle ve veritabanı adresi ({@code HMS_DB_URL}) verilmeden
 * çalışan bağlam. Veritabanı yokken sistemin güvenli tarafta kaldığını (token vermediğini) doğrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PendingIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void loginFailsClosedWithoutADatabase() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("any.user", "any-password")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INTEGRATION_PENDING"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void noBearerTokenIsAcceptedWithoutADatabase() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + "A".repeat(43)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
