package org.hmscommunity.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void healthIsAvailable() throws Exception {
        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("hms-community"));
    }

    // US-01 ile kimlik doğrulama devreye girdiğinden anonim istekler artık 403 yerine,
    // sözleşmede tanımlı olan 401 (Unauthorized) ile reddedilir.
    @Test
    void unknownRoutesAreNotPubliclyExposed() throws Exception {
        mvc.perform(get("/api/v1/patients"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/not-a-route"))
            .andExpect(status().isUnauthorized());
    }
}
