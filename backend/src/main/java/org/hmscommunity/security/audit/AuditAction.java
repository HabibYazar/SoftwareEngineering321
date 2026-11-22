package org.hmscommunity.security.audit;

/**
 * Backend'in uygulama düzeyinde ürettiği denetim olayları (US-03 / #145).
 *
 * <p>Giriş, başarısız giriş, çıkış ile hasta ve yakın kayıtlarının oluşturulması, güncellenmesi
 * (önceki/sonraki değerlerle) ve silinmesi veritabanı fonksiyonları ve tetikleyicileri tarafından,
 * değişiklikle aynı işlemde otomatik olarak kaydedilir ({@code docs/database/README.md} §8).
 * Backend bunları ayrıca üretmez; aksi halde her olay iki kez kaydedilirdi. Veritabanının kendiliğinden
 * göremediği tek olay, bir kaydın okunmasıdır.
 */
public enum AuditAction {
    /** Hasta kaydı veya elektronik tıbbi kayıt (EMR) görüntüleme; veritabanında {@code RECORD_VIEW}. */
    VIEW("RECORD_VIEW");

    private final String databaseCode;

    AuditAction(String databaseCode) {
        this.databaseCode = databaseCode;
    }

    /** {@code audit_actions.code} değeri. */
    public String databaseCode() {
        return databaseCode;
    }
}
