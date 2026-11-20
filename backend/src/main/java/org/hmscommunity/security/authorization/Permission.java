package org.hmscommunity.security.authorization;

/**
 * Rol bazlı yetkilendirmede korunan iş fonksiyonları (US-02, #132).
 * Yeni bir korumalı uç nokta eklendiğinde buraya bir izin eklenmeli ve
 * {@link RolePermissions} içinde hangi rollere verildiği açıkça tanımlanmalıdır.
 */
public enum Permission {
    /** US-06: hasta kaydı oluşturma. */
    PATIENT_REGISTER,
    /** US-07: ulusal kimlik numarası veya ada göre hasta arama. */
    PATIENT_SEARCH,
    /** US-07/US-08: tek bir hasta kaydını görüntüleme. */
    PATIENT_VIEW,
    /** US-08: hasta bilgilerini güncelleme. */
    PATIENT_UPDATE
}
