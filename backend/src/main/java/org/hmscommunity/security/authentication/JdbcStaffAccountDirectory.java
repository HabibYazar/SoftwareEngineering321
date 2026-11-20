package org.hmscommunity.security.authentication;

import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.common.persistence.ConditionalOnDatabase;
import org.hmscommunity.security.StaffRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * US-01: personel hesaplarını PostgreSQL'den okur ({@code docs/database/README.md} §7, adım 1).
 * Eşleşme, veritabanındaki benzersizlik kuralıyla aynı biçimde {@code lower(username)} üzerinden yapılır.
 */
@Repository
@ConditionalOnDatabase
class JdbcStaffAccountDirectory implements StaffAccountDirectory {

    private final JdbcClient jdbc;

    JdbcStaffAccountDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        return jdbc.sql("""
                SELECT a.id, a.username, a.password_hash, r.code AS role
                FROM user_accounts a
                JOIN staff_members s ON s.id = a.staff_member_id
                JOIN roles r ON r.id = s.role_id
                WHERE lower(a.username) = lower(?)
                """)
            .param(username)
            .query((row, rowNumber) -> new StaffAccount(
                row.getObject("id", UUID.class),
                row.getString("username"),
                row.getString("password_hash"),
                StaffRole.fromApiName(row.getString("role"))))
            .optional();
    }
}
