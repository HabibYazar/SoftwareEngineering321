package org.hmscommunity.security.authentication;

import java.util.Objects;
import java.util.UUID;
import org.hmscommunity.security.StaffRole;

/**
 * Kimlik doğrulama için gereken asgari personel hesabı görünümü.
 * Veri modeli (#114) bu alanları sağlamakla yükümlüdür; {@code passwordHash} BCrypt özetidir,
 * düz metin parola asla saklanmaz.
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
