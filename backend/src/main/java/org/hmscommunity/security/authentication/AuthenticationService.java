package org.hmscommunity.security.authentication;

import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.hmscommunity.security.audit.AuditService;
import org.hmscommunity.security.audit.AuditService.LoginFailureReason;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * US-01 (#118): personel girişi ve çıkışı; US-03 (#145): giriş/çıkış denetim olayları.
 *
 * <p>Geçersiz kimlik bilgilerinde hiçbir token üretilmez. Kullanıcı adının var olup olmadığı,
 * hem yanıt içeriğinden hem de yanıt süresinden anlaşılmasın diye bilinmeyen kullanıcılar için de
 * sahte bir BCrypt karşılaştırması yapılır.
 */
@Service
public class AuthenticationService {

    private final StaffAccountDirectory staffAccounts;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuditService audit;
    private final String timingEqualizerHash;

    public AuthenticationService(StaffAccountDirectory staffAccounts, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens, AuditService audit) {
        this.staffAccounts = staffAccounts;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.audit = audit;
        // Rastgele bir değerin özeti; hiçbir gerçek parola ile eşleşmez.
        this.timingEqualizerHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public IssuedAccessToken login(String username, String password) {
        Optional<StaffAccount> account = staffAccounts.findByUsername(username);
        String storedHash = account.map(StaffAccount::passwordHash).orElse(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (account.isEmpty() || !passwordMatches) {
            audit.loginFailed(account.map(StaffAccount::id).orElse(null),
                account.isEmpty() ? LoginFailureReason.UNKNOWN_ACCOUNT : LoginFailureReason.BAD_CREDENTIALS);
            throw new InvalidCredentialsException();
        }
        StaffAccount staffAccount = account.get();
        AuthenticatedStaff staff = new AuthenticatedStaff(staffAccount.id(), staffAccount.username(), staffAccount.role());
        IssuedAccessToken token = accessTokens.issue(staff);
        try {
            audit.loginSucceeded(staff);
        } catch (RuntimeException auditFailure) {
            // Denetim kaydı yazılamayan giriş geçerli sayılmaz: üretilen token derhal iptal edilir.
            accessTokens.resolve(token.value()).ifPresent(session -> accessTokens.revoke(session.sessionKey()));
            throw auditFailure;
        }
        return token;
    }

    /**
     * Geçerli oturumu iptal eder; aynı token sonraki isteklerde HTTP 401 alır.
     * Güvenlik açısından iptal önceliklidir, bu yüzden denetim kaydından önce yapılır.
     */
    public void logout(StaffAuthenticationToken authentication) {
        accessTokens.revoke(authentication.sessionKey());
        audit.logout(authentication.getPrincipal());
    }
}
