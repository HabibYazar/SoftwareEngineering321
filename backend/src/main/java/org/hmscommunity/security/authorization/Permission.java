package org.hmscommunity.security.authorization;

/**
 * HTTP katmanında korunan iş fonksiyonları (US-02, #132).
 *
 * <p>Adlar veritabanındaki izin kataloğunun ({@code permissions.code}, V1/V3 migration'ları) kodlarıyla
 * birebir aynıdır. Rol–izin matrisinin tek doğruluk kaynağı veritabanıdır ({@code role_permissions} ve
 * yöneticiyi her izne sahip sayan {@code has_permission()}); backend her istekte oturumun izinlerini
 * veritabanından okur ve kendi ayrı bir matris tutmaz. Böylece Administrator'ın US-05 kapsamında matrisi
 * değiştirmesi yeniden dağıtım gerektirmez ve HTTP ile veritabanı kuralları birbirinden ayrışamaz.
 *
 * <p>Veritabanında ayrı bir "arama" izni yoktur; US-07 araması kayıt görüntülemenin bir yolu olduğundan
 * {@link #PATIENT_VIEW} ile korunur.
 */
public enum Permission {
    /** US-06: hasta kaydı oluşturma. */
    PATIENT_CREATE,
    /** US-07/US-08: hasta arama ve tek bir hasta kaydını görüntüleme. */
    PATIENT_VIEW,
    /** US-08: hasta bilgilerini güncelleme. */
    PATIENT_UPDATE;

    /** Spring Security yetki adı; veritabanındaki izin koduyla aynıdır. */
    public String authority() {
        return name();
    }
}
