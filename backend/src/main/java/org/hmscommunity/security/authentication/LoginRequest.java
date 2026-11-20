package org.hmscommunity.security.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login} istek gövdesi.
 * Parola için üst sınır, BCrypt'in yalnızca ilk 72 baytı kullanmasına ve aşırı uzun girdilerle
 * kaynak tüketimine karşı konmuştur.
 */
public record LoginRequest(
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Size(max = 128) String password) {

    /** Parolanın loglara veya hata mesajlarına sızmaması için toString maskelenir. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[PROTECTED]]";
    }
}
