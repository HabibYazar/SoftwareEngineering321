package org.hmscommunity.security.audit;

/**
 * Denetim kayıtları için yalnızca-ekleme (append-only) kalıcılık arayüzü (port).
 *
 * <p>Bu arayüz bilinçli olarak güncelleme veya silme metodu içermez; böylece iş API'leri denetim
 * kayıtlarını değiştiremez veya silemez (#145 kabul kriteri). Üretim uygulaması {@code JdbcAuditEventStore}
 * olayları {@code audit_record()} ile yazar; tablo değiştirilemezliği veritabanında da korunur ({@code HL001}).
 *
 * <p>Sözleşme: olay, çağıranın veritabanı işleminde (transaction) yazılır; işlem geri alınırsa olay da
 * geri alınır. Yazılamazsa istisna fırlatılır ve çağıran iş işlemi de başarısız sayılır.
 */
public interface AuditEventStore {

    void append(AuditEvent event);
}
