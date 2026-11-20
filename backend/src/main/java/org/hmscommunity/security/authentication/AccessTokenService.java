package org.hmscommunity.security.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Opak (rastgele) erişim tokenlarını üretir ve oturum deposu ({@link StaffSessionStore}) üzerinden
 * doğrular ve iptal eder.
 *
 * <p>Tasarım kararları:
 * <ul>
 *   <li>Token, {@link SecureRandom} ile üretilen 256 bitlik rastgele değerdir; tahmin edilemez.</li>
 *   <li>Depoya yalnızca tokenın SHA-256 özeti verilir ({@code auth_sessions.token_hash}); ham token
 *       hiçbir yerde saklanmaz.</li>
 *   <li>Geçerlilik her istekte depodan sorulur. Çıkış, hesap devre dışı bırakma, rol veya parola değişikliği
 *       oturumu veritabanında sonlandırdığı anda token da geçersizleşir; bellekte ikinci bir oturum kopyası
 *       tutulmadığı için çelişen iki doğruluk kaynağı oluşmaz ve uygulama yeniden başlasa da oturumlar korunur.</li>
 *   <li>Her token yapılandırılabilir mutlak bir süre ({@code hms.security.access-token-ttl}) sonunda geçersizleşir.</li>
 * </ul>
 */
@Service
public class AccessTokenService {

    private static final int TOKEN_BYTES = 32;
    /** 32 baytın dolgusuz Base64URL gösterimi tam olarak 43 karakterdir. */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom secureRandom = new SecureRandom();
    private final StaffSessionStore sessions;
    private final Clock clock;
    private final Duration timeToLive;

    public AccessTokenService(StaffSessionStore sessions, Clock clock,
            @Value("${hms.security.access-token-ttl:PT8H}") Duration timeToLive) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("hms.security.access-token-ttl must be positive");
        }
        this.sessions = sessions;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    /**
     * Parolası doğrulanmış hesap için yeni bir oturum ve token üretir.
     * Hesap devre dışıysa oturum açılmaz ve boş döner.
     */
    public Optional<IssuedAccessToken> issue(StaffAccount account) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = clock.instant().plus(timeToLive);
        return sessions.open(account, tokenHash(rawToken), expiresAt)
            .map(sessionId -> new IssuedAccessToken(rawToken, expiresAt,
                new AuthenticatedStaff(account.id(), account.username(), account.role(), sessionId)));
    }

    /** Ham tokena karşılık gelen, süresi dolmamış ve iptal edilmemiş oturumu döndürür. */
    public Optional<StaffSession> resolve(String rawToken) {
        // Biçimi tutmayan değerler için depoya hiç gidilmez.
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            return Optional.empty();
        }
        return sessions.findActive(tokenHash(rawToken));
    }

    /** Oturumu kalıcı olarak geçersiz kılar; aynı token bir daha hiçbir isteği yetkilendiremez. */
    public boolean revoke(UUID sessionId) {
        return sessionId != null && sessions.close(sessionId);
    }

    static byte[] tokenHash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 her Java platformunda zorunlu olduğundan bu durum yalnızca bozuk bir JVM'de oluşur.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
