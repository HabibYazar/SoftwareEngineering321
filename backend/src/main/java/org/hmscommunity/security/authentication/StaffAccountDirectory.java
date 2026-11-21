package org.hmscommunity.security.authentication;

import java.util.Optional;

/**
 * Personel hesaplarına erişim için port; üretim uygulaması {@code JdbcStaffAccountDirectory}'dir
 * ({@code user_accounts}, {@code staff_members}, {@code roles}).
 *
 * <p>Sözleşme:
 * <ul>
 *   <li>Kullanıcı adı büyük/küçük harf DUYARSIZ eşleşir. Veritabanı kullanıcı adlarını küçük harfe çevirip
 *       saklar ve benzersizliği {@code lower(username)} üzerinde tutar; ilk taslaktaki "birebir eşleşme"
 *       kuralı bu nedenle veritabanıyla tutarlı olacak şekilde değiştirilmiştir.</li>
 *   <li>{@link StaffAccount#passwordHash()} BCrypt özetidir ({@code $2a$}/{@code $2b$}/{@code $2y$},
 *       isteğe bağlı {@code {bcrypt}} ön ekiyle).</li>
 *   <li>Rol, {@link org.hmscommunity.security.StaffRole} içindeki yedi rolden biridir.</li>
 *   <li>Devre dışı hesaplar da döndürülür; oturum açmayı oturum deposu reddeder.</li>
 * </ul>
 * Veritabanı yapılandırılmamışsa geçici {@link UnavailableStaffAccountDirectory} kullanılır (HTTP 503).
 */
public interface StaffAccountDirectory {

    Optional<StaffAccount> findByUsername(String username);
}
