package org.hmscommunity.security.authentication;

import java.time.Instant;
import org.hmscommunity.security.AuthenticatedStaff;

/**
 * Başarılı girişte istemciye bir kez verilen ham token ve oturum bilgisi.
 * Ham token yalnızca yanıt gövdesine yazılır; sunucuda saklanmaz ve loglanmaz.
 */
public record IssuedAccessToken(String value, Instant expiresAt, AuthenticatedStaff staff) {

    /** Ham tokenın loglara sızmaması için toString maskelenir. */
    @Override
    public String toString() {
        return "IssuedAccessToken[value=[PROTECTED], expiresAt=" + expiresAt + ", staff=" + staff + "]";
    }
}
