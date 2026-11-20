package org.hmscommunity.security.audit;

/**
 * Denetim kayıtları için yalnızca-ekleme (append-only) kalıcılık arayüzü (port).
 *
 * <p>Bu arayüz bilinçli olarak güncelleme veya silme metodu içermez; böylece iş API'leri
 * denetim kayıtlarını değiştiremez veya silemez (#145 kabul kriteri).
 *
 * <p>Kalıcı uygulama #140 ("US-03 Logging of all actions", veri iş akışı) tarafından sağlanmalıdır.
 * Beklenen sözleşme:
 * <ul>
 *   <li>{@link AuditEvent} alanlarının tamamı (aktör, rol, işlem, kayıt, zaman, değişiklikler, ayrıntılar) saklanır.</li>
 *   <li>Uygulama veritabanı kullanıcısının tabloda yalnızca INSERT/SELECT yetkisi olur (UPDATE/DELETE yok).</li>
 *   <li>İşlem (ör. hasta güncelleme) ile denetim kaydı aynı veritabanı işleminde (transaction) yazılır.</li>
 *   <li>Yazılamazsa istisna fırlatılır; çağıran iş işlemi de başarısız sayılır.</li>
 * </ul>
 * Uygulama bean olarak eklendiğinde geçici {@link LoggingAuditEventStore} otomatik olarak devre dışı kalır.
 */
public interface AuditEventStore {

    void append(AuditEvent event);
}
