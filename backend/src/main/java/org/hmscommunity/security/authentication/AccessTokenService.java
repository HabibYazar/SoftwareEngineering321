package org.hmscommunity.security.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Opak (rastgele) erişim tokenlarını üretir, doğrular ve iptal eder.
 *
 * <p>Tasarım kararları:
 * <ul>
 *   <li>Token, {@link SecureRandom} ile üretilen 256 bitlik rastgele değerdir; tahmin edilemez.</li>
 *   <li>Sunucuda yalnızca tokenın SHA-256 özeti saklanır; ham token hiçbir yerde tutulmaz.</li>
 *   <li>Oturum sunucu tarafında tutulduğu için çıkış (logout) tokenı anında ve gerçekten geçersiz kılar
 *       (imzalı JWT'lerdeki "süresi dolana kadar geçerli" sorunu yoktur).</li>
 *   <li>Her token yapılandırılabilir mutlak bir süre ({@code hms.security.access-token-ttl}) sonunda geçersizleşir.</li>
 * </ul>
 * Kısıt: Oturumlar bellek içinde tutulur; uygulama yeniden başlatıldığında tüm oturumlar sonlanır ve
 * birden fazla uygulama örneği arasında paylaşılmaz. Tek örnekli akademik demo için bilinçli bir tercihtir.
 */
@Service
public class AccessTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, StaffSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration timeToLive;

    public AccessTokenService(Clock clock, @Value("${hms.security.access-token-ttl:PT8H}") Duration timeToLive) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("hms.security.access-token-ttl must be positive");
        }
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    public IssuedAccessToken issue(AuthenticatedStaff staff) {
        purgeExpired();
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = clock.instant().plus(timeToLive);
        String sessionKey = sessionKeyOf(rawToken);
        sessions.put(sessionKey, new StaffSession(sessionKey, staff, expiresAt));
        return new IssuedAccessToken(rawToken, expiresAt, staff);
    }

    /** Ham tokena karşılık gelen, süresi dolmamış ve iptal edilmemiş oturumu döndürür. */
    public Optional<StaffSession> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String sessionKey = sessionKeyOf(rawToken);
        StaffSession session = sessions.get(sessionKey);
        if (session == null) {
            return Optional.empty();
        }
        if (isExpired(session)) {
            sessions.remove(sessionKey, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Oturumu kalıcı olarak geçersiz kılar; aynı token bir daha hiçbir isteği yetkilendiremez. */
    public boolean revoke(String sessionKey) {
        return sessionKey != null && sessions.remove(sessionKey) != null;
    }

    int activeSessionCount() {
        return sessions.size();
    }

    private boolean isExpired(StaffSession session) {
        return !clock.instant().isBefore(session.expiresAt());
    }

    private void purgeExpired() {
        sessions.values().removeIf(this::isExpired);
    }

    static String sessionKeyOf(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 her Java platformunda zorunlu olduğundan bu durum yalnızca bozuk bir JVM'de oluşur.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
