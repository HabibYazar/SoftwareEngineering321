package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginAs;
import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.InMemoryStaffAccountDirectory;
import org.hmscommunity.testsupport.InMemoryStaffSessionStore;
import org.hmscommunity.testsupport.SyntheticStaff;
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

/** US-01 kabul kriterlerinin HTTP katmanında, gerçek güvenlik filtre zinciriyle doğrulanması. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDoublesConfiguration.class)
class AuthenticationApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired InMemoryStaffAccountDirectory staffAccounts;
    @Autowired InMemoryStaffSessionStore sessions;

    @BeforeEach
    void resetState() {
        staffAccounts.resetToSyntheticAccounts();
        sessions.reset();
    }

    // --- US-03: başarısız denemeler oturum deposuna (veritabanında auth_login_failure) iletilir ---

    @Test
    void failedLoginIsRecordedButMalformedRequestIsNot() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.RECEPTIONIST), "wrong-password")))
            .andExpect(status().isUnauthorized());

        assertThat(sessions.failedLogins()).hasSize(1);
    }

    @Test
    void deactivatedAccountCannotLogInAndLosesItsSessions() throws Exception {
        String token = loginAs(mvc, StaffRole.RECEPTIONIST);

        sessions.deactivate(staffAccounts.get(StaffRole.RECEPTIONIST).id());

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.RECEPTIONIST), SyntheticStaff.PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void validCredentialsReturnBearerTokenAndRole() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.RECEPTIONIST), SyntheticStaff.PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value(hasLength(43)))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.role").value("Receptionist"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void wrongPasswordReturns401WithoutToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.RECEPTIONIST), "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("Invalid username or password."))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unknownUserIsIndistinguishableFromWrongPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("no.such.user", SyntheticStaff.PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("Invalid username or password."))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void blankCredentialsReturnFieldLevel400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" \",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[?(@.field == 'username')].code").value("NotBlank"))
            .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("NotBlank"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void logoutInvalidatesTheToken() throws Exception {
        String token = loginAs(mvc, StaffRole.PHYSICIAN);

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent());

        // Aynı token artık hiçbir isteği yetkilendiremez.
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutOnlyRevokesTheCallersOwnSession() throws Exception {
        String first = loginAs(mvc, StaffRole.NURSE);
        String second = loginAs(mvc, StaffRole.NURSE);

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(first)))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(second)))
            .andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer("forged-token-value")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void nonBearerSchemeIsRejected() throws Exception {
        String token = loginAs(mvc, StaffRole.NURSE);

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Basic " + token))
            .andExpect(status().isUnauthorized());
    }
}
