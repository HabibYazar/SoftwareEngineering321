package org.hmscommunity.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authentication.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gerçek PostgreSQL ile çalışan, test dublörü İÇERMEYEN uygulama bağlamı. Alt sınıflar
 * {@link RequiresPostgres} ile işaretlenmelidir.
 *
 * <p>Uygulama {@code HMS_DB_URL} ile çalışırken kullandığı yapılandırmanın aynısıyla başlatılır: veri kaynağı
 * en az yetkili giriş kullanıcısıdır, Flyway ise yönetici olarak çalışır (şema zaten güncel olduğu için yalnızca
 * doğrular).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired protected MockMvc mvc;
    @Autowired protected AccessTokenService accessTokens;

    /** Bu testin başlangıç anı (veritabanı saatiyle); yalnızca bu testte üretilen denetim kayıtlarını seçmek için. */
    protected OffsetDateTime testStartedAt;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> database().jdbcUrl());
        registry.add("spring.datasource.username", () -> database().appUser());
        registry.add("spring.datasource.password", () -> database().appPassword());
        registry.add("spring.flyway.user", () -> database().adminUser());
        registry.add("spring.flyway.password", () -> database().adminPassword());
        registry.add("spring.flyway.locations", () -> String.join(",", PostgresTestDatabase.MIGRATION_AND_SEED_LOCATIONS));
    }

    protected static PostgresTestDatabase database() {
        return PostgresTestDatabase.get();
    }

    @BeforeEach
    void rememberStart() {
        testStartedAt = database().admin().sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single();
    }

    /** Gerçek giriş uç noktasıyla token alır. */
    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(ApiTestSupport.loginJson(username, password)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("accessToken").asText();
    }

    protected String login(StaffRole role) throws Exception {
        return login(SyntheticStaff.username(role), SyntheticStaff.PASSWORD);
    }

    /** Gerçek bir oturumla giriş yapmış personel (servis/adaptör çağrıları için aktör). */
    protected AuthenticatedStaff signedIn(StaffRole role) throws Exception {
        return accessTokens.resolve(login(role)).orElseThrow().staff();
    }

    /** Bu test başladıktan sonra yazılmış denetim kayıtları, yazılış sırasıyla. */
    protected List<AuditRow> auditEventsSinceTestStart() {
        return database().admin().sql("""
                SELECT a.code AS action, e.code AS entity_type, l.entity_id, l.actor_user_id, l.session_id,
                       r.code AS actor_role, l.result, l.details::text AS details
                FROM audit_log l
                JOIN audit_actions a ON a.id = l.action_id
                LEFT JOIN audit_entity_types e ON e.id = l.entity_type_id
                LEFT JOIN roles r ON r.id = l.actor_role_id
                WHERE l.occurred_at >= ?
                ORDER BY l.id
                """)
            .param(testStartedAt)
            .query((row, rowNumber) -> new AuditRow(row.getString("action"), row.getString("entity_type"),
                row.getObject("entity_id", UUID.class), row.getObject("actor_user_id", UUID.class),
                row.getObject("session_id", UUID.class), row.getString("actor_role"), row.getInt("result"),
                parse(row.getString("details"))))
            .list();
    }

    protected List<AuditRow> auditEventsSinceTestStart(String action) {
        return auditEventsSinceTestStart().stream().filter(row -> row.action().equals(action)).toList();
    }

    private static JsonNode parse(String json) {
        try {
            return json == null ? JSON.nullNode() : JSON.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * {@code audit_log} satırı (kodlar çözülmüş).
     *
     * @param result 1 SUCCESS, 2 FAILURE, 3 DENIED
     */
    public record AuditRow(String action, String entityType, UUID entityId, UUID actorId, UUID sessionId,
            String actorRole, int result, JsonNode details) {
    }
}
