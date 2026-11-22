package org.hmscommunity.security.authentication;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * US-01 (#118): personel girişi ve çıkışı.
 *
 * <p>Geçersiz kimlik bilgilerinde hiçbir token üretilmez. Kullanıcı adının var olup olmadığı,
 * hem yanıt içeriğinden hem de yanıt süresinden anlaşılmasın diye bilinmeyen kullanıcılar için de
 * sahte bir BCrypt karşılaştırması yapılır. Devre dışı hesaplar da aynı genel 401 yanıtını alır.
 *
 * <p>US-03: giriş, başarısız giriş ve çıkış denetim kayıtları oturum deposunun SQL fonksiyonları
 * ({@code auth_login_success}, {@code auth_login_failure}, {@code auth_logout}) tarafından yazılır.
 * Bu sınıf bilinçli olarak {@code @Transactional} DEĞİLDİR: başarısız giriş kaydı, isteğin 401 ile
 * bitmesine rağmen kalıcı olmalıdır; her fonksiyon çağrısı kendi işleminde tamamlanır.
 */
@Service
public class AuthenticationService {

    private final StaffAccountDirectory staffAccounts;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final StaffSessionStore sessions;
    private final String timingEqualizerHash;

    public AuthenticationService(StaffAccountDirectory staffAccounts, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens, StaffSessionStore sessions) {
        this.staffAccounts = staffAccounts;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.sessions = sessions;
        // Rastgele bir değerin özeti; hiçbir gerçek parola ile eşleşmez.
        this.timingEqualizerHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public IssuedAccessToken login(String username, String password) {
        Optional<StaffAccount> account = staffAccounts.findByUsername(username.strip());
        String storedHash = account.map(StaffAccount::passwordHash).orElse(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (account.isEmpty()) {
            sessions.recordFailedLogin(null, LoginFailureReason.UNKNOWN_ACCOUNT);
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches) {
            sessions.recordFailedLogin(account.get(), LoginFailureReason.BAD_CREDENTIALS);
            throw new InvalidCredentialsException();
        }
        // Devre dışı hesapta oturum açılmaz; veritabanı bu denemeyi ACCOUNT_DEACTIVATED nedeniyle kaydeder.
        return accessTokens.issue(account.get()).orElseThrow(InvalidCredentialsException::new);
    }

    /** Geçerli oturumu iptal eder; aynı token sonraki isteklerde HTTP 401 alır. */
    public void logout(StaffAuthenticationToken authentication) {
        accessTokens.revoke(authentication.sessionId());
    }
}
