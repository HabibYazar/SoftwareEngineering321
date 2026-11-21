package org.hmscommunity.security.authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Oturum yaşam döngüsü için port. Tokenın geçerliliğinin TEK doğruluk kaynağı bu arayüzün arkasındaki
 * depodur (üretimde PostgreSQL {@code auth_sessions}); backend bellekte ayrıca oturum tutmaz.
 *
 * <p>Metotlar Data iş akışının SQL fonksiyonlarına birebir karşılık gelir ve onların davranışını devralır:
 * giriş/çıkış denetim kayıtları ({@code LOGIN_SUCCESS}, {@code LOGIN_FAILURE}, {@code LOGOUT}) bu
 * fonksiyonların içinde, oturum değişikliğiyle aynı işlemde yazılır. Bu nedenle backend bu olaylar için
 * ayrıca denetim kaydı üretmez (çift kayıt olmaz). Ham token hiçbir zaman bu arayüze verilmez; yalnızca
 * SHA-256 özeti (32 bayt) iletilir.
 */
public interface StaffSessionStore {

    /**
     * {@code auth_login_success}: parolası doğrulanmış hesap için oturum açar. Hesap devre dışıysa oturum
     * açılmaz, başarısız giriş kaydedilir ve boş döner.
     */
    Optional<UUID> open(StaffAccount account, byte[] tokenHash, Instant expiresAt);

    /**
     * {@code auth_login_failure}: başarısız girişi kaydeder ve eşleşen hesabın hatalı deneme sayacını artırır.
     * Gizlilik gereği kullanıcının yazdığı metin değil, yalnızca eşleşen hesabın kendi kullanıcı adı iletilir;
     * yazılan metin yanlışlıkla kullanıcı adı alanına girilmiş bir parola olabilir.
     *
     * @param matchedAccount kullanıcı adıyla eşleşen hesap; hesap yoksa {@code null}
     */
    void recordFailedLogin(StaffAccount matchedAccount, LoginFailureReason reason);

    /**
     * {@code auth_validate_session}: süresi dolmamış, iptal edilmemiş ve hesabı etkin bir oturum varsa
     * oturumu güncel rol ve izinleriyle döndürür.
     */
    Optional<StaffSession> findActive(byte[] tokenHash);

    /** {@code auth_logout}: oturumu hemen sonlandırır; zaten sonlanmışsa {@code false} döner. */
    boolean close(UUID sessionId);
}
