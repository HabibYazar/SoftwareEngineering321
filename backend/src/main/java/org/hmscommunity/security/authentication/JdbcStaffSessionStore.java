package org.hmscommunity.security.authentication;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hmscommunity.common.persistence.ConditionalOnDatabase;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.StaffRole;
import org.hmscommunity.security.authorization.Permission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Oturum yaşam döngüsünü Data iş akışının SQL fonksiyonlarına devreder ({@code docs/database/README.md} §7).
 * Oturum durumu ve giriş/çıkış denetim kayıtları tamamen bu fonksiyonlarda üretilir; burada tekrarlanmaz.
 */
@Repository
@ConditionalOnDatabase
class JdbcStaffSessionStore implements StaffSessionStore {

    private final JdbcClient jdbc;

    JdbcStaffSessionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> open(StaffAccount account, byte[] tokenHash, Instant expiresAt) {
        // NULL dönüşü: hesap devre dışı (fonksiyon başarısız girişi kendisi kaydeder).
        return jdbc.sql("SELECT auth_login_success(?, ?, ?)")
            .params(account.id(), tokenHash, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            .query((row, rowNumber) -> Optional.ofNullable(row.getObject(1, UUID.class)))
            .single();
    }

    @Override
    public void recordFailedLogin(StaffAccount matchedAccount, LoginFailureReason reason) {
        jdbc.sql("SELECT auth_login_failure(p_username => ?, p_reason => ?)")
            .params(matchedAccount == null ? null : matchedAccount.username(), reason.name())
            .query((row, rowNumber) -> Boolean.TRUE)
            .single();
    }

    @Override
    public Optional<StaffSession> findActive(byte[] tokenHash) {
        // İzinler veritabanının kendi kuralıyla (has_permission: yönetici her izne sahiptir) hesaplanır.
        return jdbc.sql("""
                SELECT v.session_id, v.account_id, v.role, v.expires_at, a.username,
                       ARRAY(SELECT p.code FROM permissions p
                             WHERE has_permission(v.account_id, p.code) ORDER BY p.code) AS permissions
                FROM auth_validate_session(?) v
                JOIN user_accounts a ON a.id = v.account_id
                """)
            .param(tokenHash)
            .query((row, rowNumber) -> new StaffSession(
                new AuthenticatedStaff(row.getObject("account_id", UUID.class), row.getString("username"),
                    StaffRole.fromApiName(row.getString("role")), row.getObject("session_id", UUID.class)),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                permissions(row)))
            .optional();
    }

    @Override
    public boolean close(UUID sessionId) {
        return jdbc.sql("SELECT auth_logout(?)")
            .param(sessionId)
            .query(Boolean.class)
            .single();
    }

    /** Backend'in uç nokta korumasında kullanmadığı izin kodları (ör. STAFF_ACCOUNT_MANAGE) yok sayılır. */
    private static Set<Permission> permissions(ResultSet row) throws SQLException {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        Array codes = row.getArray("permissions");
        for (Object code : (Object[]) codes.getArray()) {
            for (Permission permission : Permission.values()) {
                if (permission.name().equals(code)) {
                    permissions.add(permission);
                }
            }
        }
        return permissions;
    }
}
