package org.hmscommunity.security.authentication;

import java.util.Optional;
import java.util.UUID;
import org.hmscommunity.security.AuthenticatedStaff;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * US-01 (#118): personel girişi ve çıkışı.
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
    private final String timingEqualizerHash;

    public AuthenticationService(StaffAccountDirectory staffAccounts, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens) {
        this.staffAccounts = staffAccounts;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        // Rastgele bir değerin özeti; hiçbir gerçek parola ile eşleşmez.
        this.timingEqualizerHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public IssuedAccessToken login(String username, String password) {
        Optional<StaffAccount> account = staffAccounts.findByUsername(username);
        String storedHash = account.map(StaffAccount::passwordHash).orElse(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (account.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        StaffAccount staffAccount = account.get();
        AuthenticatedStaff staff = new AuthenticatedStaff(staffAccount.id(), staffAccount.username(), staffAccount.role());
        return accessTokens.issue(staff);
    }

    /** Geçerli oturumu iptal eder; aynı token sonraki isteklerde HTTP 401 alır. */
    public void logout(StaffAuthenticationToken authentication) {
        accessTokens.revoke(authentication.sessionKey());
    }
}
