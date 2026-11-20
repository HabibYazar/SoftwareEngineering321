package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.hmscommunity.testsupport.TestDoublesConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ayrıntılı (DEBUG) loglama açıkken bile parola ve ham tokenın log çıktısına yazılmadığını doğrular.
 */
@SpringBootTest(properties = {
    "logging.level.org.springframework.security=DEBUG",
    "logging.level.org.springframework.web=DEBUG",
    "logging.level.org.hmscommunity=DEBUG"
})
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CredentialLoggingTest {

    @Autowired MockMvc mvc;

    @Test
    void passwordsAndTokensNeverReachTheLogs(CapturedOutput output) throws Exception {
        String wrongPassword = "another-synthetic-wrong-Pass9";
        mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginJson(SyntheticStaff.username(StaffRole.NURSE), wrongPassword)));
        String token = loginAs(mvc, StaffRole.NURSE);
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)));
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)));

        assertThat(output.getAll())
            .doesNotContain(SyntheticStaff.PASSWORD)
            .doesNotContain(wrongPassword)
            .doesNotContain(token)
            .doesNotContain("Using generated security password");
    }
}
