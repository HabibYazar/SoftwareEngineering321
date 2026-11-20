package org.hmscommunity.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hmscommunity.testsupport.ApiTestSupport.bearer;
import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.PostgresIntegrationTest;
import org.hmscommunity.testsupport.PostgresTestDatabase;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * US-01 (#118) ve US-03 (#145): gerçek PostgreSQL'e karşı giriş, başarısız giriş, devre dışı hesap ve çıkış.
 * Oturum durumu ({@code auth_sessions}) ve denetim kayıtları ({@code audit_log}) doğrudan veritabanından okunur.
 */
@RequiresPostgres
class PostgresAuthenticationTest extends PostgresIntegrationTest {

    @Test
    void successfulLoginOpensADatabaseSessionStoringOnlyTheTokenHash() throws Exception {
        String token = login(StaffRole.RECEPTIONIST);

        Map<String, Object> session = database().admin().sql("""
                SELECT s.id, s.user_account_id, s.token_hash, s.revoked_at, s.expires_at > now() AS live
                FROM auth_sessions s WHERE s.token_hash = ?
                """)
            .param(AccessTokenService.tokenHash(token))
            .query().singleRow();
        assertThat(session.get("user_account_id")).isEqualTo(database().accountId(StaffRole.RECEPTIONIST));
        assertThat(session.get("revoked_at")).isNull();
        assertThat(session.get("live")).isEqualTo(true);
        // Satır ham tokenla değil, yalnızca 32 baytlık SHA-256 özetiyle bulunabilir.
        assertThat((byte[]) session.get("token_hash")).hasSize(32).isNotEqualTo(token.getBytes());

        assertThat(auditEventsSinceTestStart("LOGIN_SUCCESS")).singleElement().satisfies(event -> {
            assertThat(event.entityType()).isEqualTo("AUTH_SESSION");
            assertThat(event.entityId()).isEqualTo(session.get("id"));
            assertThat(event.actorId()).isEqualTo(database().accountId(StaffRole.RECEPTIONIST));
            assertThat(event.sessionId()).isEqualTo(session.get("id"));
            assertThat(event.actorRole()).isEqualTo("Receptionist");
            assertThat(event.result()).isEqualTo(1);
        });
    }

    @Test
    void everyOneOfTheSevenRolesCanLogInWithItsDatabaseRole() throws Exception {
        for (StaffRole role : StaffRole.values()) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(SyntheticStaff.username(role), SyntheticStaff.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.apiName()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }
    }

    @Test
    void usernameMatchingIsCaseInsensitiveLikeTheDatabaseIndex() throws Exception {
        String token = login(SyntheticStaff.username(StaffRole.NURSE).toUpperCase(Locale.ROOT), SyntheticStaff.PASSWORD);

        assertThat(accessTokens.resolve(token)).hasValueSatisfying(session ->
            assertThat(session.staff().username()).isEqualTo(SyntheticStaff.username(StaffRole.NURSE)));
    }

    @Test
    void wrongPasswordIsRejectedCountedAndAuditedWithoutTheTypedPassword() throws Exception {
        UUID nurse = database().accountId(StaffRole.NURSE);
        int failuresBefore = failedLoginCount(nurse);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.NURSE), "synthetic-wrong-Pass7")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(failedLoginCount(nurse)).isEqualTo(failuresBefore + 1);
        assertThat(auditEventsSinceTestStart("LOGIN_FAILURE")).singleElement().satisfies(event -> {
            assertThat(event.actorId()).isNull();
            assertThat(event.entityType()).isEqualTo("USER_ACCOUNT");
            assertThat(event.entityId()).isEqualTo(nurse);
            assertThat(event.result()).isEqualTo(2);
            assertThat(event.details().get("reason").asText()).isEqualTo("BAD_CREDENTIALS");
            assertThat(event.details().get("username").asText()).isEqualTo(SyntheticStaff.username(StaffRole.NURSE));
            assertThat(event.details().toString()).doesNotContain("synthetic-wrong-Pass7");
        });
    }

    @Test
    void unknownUsernameIsAuditedWithoutStoringTheTypedText() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("maybe-a-mistyped-password", SyntheticStaff.PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        assertThat(auditEventsSinceTestStart("LOGIN_FAILURE")).singleElement().satisfies(event -> {
            assertThat(event.entityId()).isNull();
            assertThat(event.details().get("reason").asText()).isEqualTo("UNKNOWN_ACCOUNT");
            assertThat(event.details().toString()).doesNotContain("maybe-a-mistyped-password");
        });
    }

    @Test
    void deactivatedAccountIsRejectedWithTheGenericErrorAndNoSession() throws Exception {
        long sessionsBefore = sessionCount(database().deactivatedAccountId());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(PostgresTestDatabase.DEACTIVATED_USERNAME, SyntheticStaff.PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(sessionCount(database().deactivatedAccountId())).isEqualTo(sessionsBefore);
        assertThat(auditEventsSinceTestStart("LOGIN_FAILURE")).singleElement().satisfies(event -> {
            assertThat(event.result()).isEqualTo(3);
            assertThat(event.details().get("reason").asText()).isEqualTo("ACCOUNT_DEACTIVATED");
        });
    }

    @Test
    void logoutEndsTheDatabaseSessionImmediately() throws Exception {
        String token = login(StaffRole.PHYSICIAN);
        UUID sessionId = accessTokens.resolve(token).orElseThrow().sessionId();

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent());

        Map<String, Object> session = database().admin().sql(
                "SELECT revoked_at IS NOT NULL AS revoked, revoke_reason FROM auth_sessions WHERE id = ?")
            .param(sessionId).query().singleRow();
        assertThat(session).containsEntry("revoked", true).containsEntry("revoke_reason", "LOGOUT");
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(auditEventsSinceTestStart("LOGOUT")).singleElement().satisfies(event -> {
            assertThat(event.entityId()).isEqualTo(sessionId);
            assertThat(event.actorId()).isEqualTo(database().accountId(StaffRole.PHYSICIAN));
        });
    }

    /** Tek doğruluk kaynağı veritabanıdır: oturumu veritabanında biten token bir sonraki istekte geçersizdir. */
    @Test
    void sessionEndedInTheDatabaseInvalidatesTheTokenOnTheNextRequest() throws Exception {
        String token = login(StaffRole.LAB_TECHNICIAN);
        UUID sessionId = accessTokens.resolve(token).orElseThrow().sessionId();

        database().asAdministrator("PERFORM auth_revoke_session(?)", sessionId);

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void prefixedBcryptHashFromTheBootstrapDocumentationIsAccepted() throws Exception {
        String username = "test.prefixed-" + UUID.randomUUID().toString().substring(0, 8);
        String hash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(SyntheticStaff.PASSWORD);
        UUID staffId = UUID.randomUUID();
        database().asAdministrator("INSERT INTO staff_members (id, role_id, first_name, last_name) "
            + "SELECT ?, id, 'Sentetik', 'Onekli' FROM roles WHERE code = 'Nurse'", staffId);
        database().asAdministrator("INSERT INTO user_accounts (staff_member_id, username, password_hash) VALUES (?, ?, ?)",
            staffId, username, hash);

        assertThat(login(username, SyntheticStaff.PASSWORD)).hasSize(43);
    }

    private int failedLoginCount(UUID accountId) {
        return database().admin().sql("SELECT failed_login_count FROM user_accounts WHERE id = ?")
            .param(accountId).query(Integer.class).single();
    }

    private long sessionCount(UUID accountId) {
        return database().admin().sql("SELECT count(*) FROM auth_sessions WHERE user_account_id = ?")
            .param(accountId).query(Long.class).single();
    }
}
