package org.hmscommunity.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hmscommunity.testsupport.ApiTestSupport.loginJson;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hmscommunity.common.persistence.PostgresErrors;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.testsupport.PostgresIntegrationTest;
import org.hmscommunity.testsupport.RequiresPostgres;
import org.hmscommunity.testsupport.SyntheticStaff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * US-03 (#145): denetim kayıtlarının gerçek PostgreSQL'de değiştirilemezliği ve gizliliği.
 * Uygulama kullanıcısı tabloya hiç erişemez; tablo sahibi bile kayıtları değiştiremez veya silemez.
 */
@RequiresPostgres
class PostgresAuditTest extends PostgresIntegrationTest {

    /** Uygulamanın kendi bağlantısı (yalnızca hms_app yetkileri). */
    @Autowired JdbcClient applicationJdbc;
    @Autowired AuditService audit;

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT count(*) FROM audit_log",
        "UPDATE audit_log SET result = 1",
        "DELETE FROM audit_log",
        "INSERT INTO audit_log (action_id, result) VALUES (20, 1)"})
    void applicationRoleHasNoDirectAccessToTheAuditLog(String statement) {
        assertThatThrownBy(() -> applicationJdbc.sql(statement).query().listOfRows())
            .isInstanceOfSatisfying(DataAccessException.class, failure ->
                assertThat(PostgresErrors.hasSqlState(failure, "42501")).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE audit_log SET result = result",
        "DELETE FROM audit_log WHERE false",
        "TRUNCATE audit_log",
        "INSERT INTO audit_log (action_id, result) VALUES (20, 1)"})
    void evenTheOwnerCannotChangeAuditHistory(String statement) {
        assertThatThrownBy(() -> database().admin().sql(statement).update())
            .isInstanceOfSatisfying(DataAccessException.class, failure ->
                assertThat(PostgresErrors.hasSqlState(failure, "HL001")).isTrue());
    }

    @Test
    void passwordsAndTokensNeverReachTheAuditLog() throws Exception {
        String wrongPassword = "synthetic-wrong-Pass8";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(SyntheticStaff.username(StaffRole.NURSE), wrongPassword)))
            .andExpect(status().isUnauthorized());
        String token = login(StaffRole.NURSE);

        long leaks = database().admin().sql("""
                SELECT count(*) FROM audit_log
                WHERE details::text LIKE '%' || ? || '%' OR details::text LIKE '%' || ? || '%'
                   OR details::text LIKE '%' || ? || '%'
                """)
            .params(wrongPassword, SyntheticStaff.PASSWORD, token)
            .query(Long.class).single();
        assertThat(leaks).isZero();
        assertThat(auditEventsSinceTestStart()).extracting(AuditRow::action)
            .containsExactly("LOGIN_FAILURE", "LOGIN_SUCCESS");
    }

    /**
     * Veritabanında henüz tıbbi kayıt türü yoktur ({@code audit_entity_types}); EMR görüntüleme kaydı sessizce
     * kaybolmaz, reddedilir. EMR migration'ı türü eklediğinde bu test güncellenmelidir (Data iş akışı, Sprint 3).
     */
    @Test
    void medicalRecordViewIsRejectedUntilTheDatabaseDefinesTheEntityType() throws Exception {
        AuthenticatedStaff receptionist = signedIn(StaffRole.RECEPTIONIST);

        assertThatThrownBy(() -> audit.medicalRecordViewed(receptionist, UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOfSatisfying(DataAccessException.class, failure ->
                assertThat(PostgresErrors.hasSqlState(failure, "HL002")).isTrue());
    }
}
