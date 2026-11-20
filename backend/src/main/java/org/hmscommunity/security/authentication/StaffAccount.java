package org.hmscommunity.security.authentication;

import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.StaffRole;

/**
 * Kimlik doğrulama için gereken asgari personel hesabı görünümü ({@code user_accounts} + rolü).
 *
 * @param id kullanıcı hesabının kimliği ({@code user_accounts.id}); veritabanı aktör bağlamında kullanılır
 * @param username veritabanında küçük harfe çevrilmiş olarak saklanan kullanıcı adı
 * @param passwordHash BCrypt özeti; düz metin parola asla saklanmaz
 */
public record StaffAccount(UUID id, String username, String passwordHash, StaffRole role) {

    public StaffAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
    }

    /** Parola özetinin yanlışlıkla loglara düşmemesi için toString maskelenir. */
    @Override
    public String toString() {
        return "StaffAccount[id=" + id + ", username=" + username + ", role=" + role + ", passwordHash=[PROTECTED]]";
    }
}
