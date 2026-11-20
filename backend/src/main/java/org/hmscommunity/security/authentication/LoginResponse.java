package org.hmscommunity.security.authentication;

import java.time.Instant;
import org.hmscommunity.security.StaffRole;

/**
 * OpenAPI {@code LoginResponse}: erişim tokenı, token tipi, rol ve tokenın geçerlilik bitiş zamanı.
 */
public record LoginResponse(String accessToken, String tokenType, StaffRole role, Instant expiresAt) {

    static LoginResponse from(IssuedAccessToken token) {
        return new LoginResponse(token.value(), "Bearer", token.staff().role(), token.expiresAt());
    }

    /** Ham tokenın loglara sızmaması için toString maskelenir. */
    @Override
    public String toString() {
        return "LoginResponse[accessToken=[PROTECTED], tokenType=" + tokenType + ", role=" + role
            + ", expiresAt=" + expiresAt + "]";
    }
}
