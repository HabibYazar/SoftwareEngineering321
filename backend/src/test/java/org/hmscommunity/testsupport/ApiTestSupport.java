package org.hmscommunity.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.hmscommunity.security.StaffRole;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Entegrasyon testlerinde gerçek giriş akışı üzerinden token almak için yardımcılar. */
public final class ApiTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ApiTestSupport() {
    }

    public static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String loginJson(String username, String password) {
        return json(Map.of("username", username, "password", password));
    }

    /** Belirtilen rolün sentetik hesabıyla giriş yapar ve ham erişim tokenını döndürür. */
    public static String loginAs(MockMvc mvc, StaffRole role) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(role), SyntheticStaff.PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
