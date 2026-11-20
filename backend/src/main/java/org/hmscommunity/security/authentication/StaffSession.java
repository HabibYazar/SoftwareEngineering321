package org.hmscommunity.security.authentication;

import java.time.Instant;
import org.hmscommunity.security.AuthenticatedStaff;

/**
 * Sunucu tarafında tutulan oturum kaydı.
 * {@code sessionKey} ham token değil, tokenın SHA-256 özetidir; bu nedenle loglansa bile
 * isteği yetkilendirmek için kullanılamaz.
 */
public record StaffSession(String sessionKey, AuthenticatedStaff staff, Instant expiresAt) {
}
