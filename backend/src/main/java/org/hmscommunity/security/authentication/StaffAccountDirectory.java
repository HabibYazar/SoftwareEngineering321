package org.hmscommunity.security.authentication;

import java.util.Optional;

/**
 * Personel hesaplarına erişim için backend tarafındaki entegrasyon arayüzü (port).
 *
 * <p>Kalıcı uygulama, #114 ("User and Role Data Model", veri iş akışı) tarafından sağlanmalıdır.
 * Beklenen sözleşme:
 * <ul>
 *   <li>Kullanıcı adı benzersizdir ve birebir (büyük/küçük harf duyarlı) eşleşir.</li>
 *   <li>{@link StaffAccount#passwordHash()} BCrypt özetidir ({@code $2a$}, {@code $2b$} veya {@code $2y$}).</li>
 *   <li>Rol, {@link org.hmscommunity.security.StaffRole} içindeki yedi rolden biridir.</li>
 * </ul>
 * Uygulama bir {@code @Repository}/{@code @Component} olarak eklendiğinde geçici
 * {@link UnavailableStaffAccountDirectory} otomatik olarak devre dışı kalır.
 */
public interface StaffAccountDirectory {

    Optional<StaffAccount> findByUsername(String username);
}
