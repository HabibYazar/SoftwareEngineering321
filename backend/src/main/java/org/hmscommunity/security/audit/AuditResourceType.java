package org.hmscommunity.security.audit;

/**
 * Denetim kaydının ilgili olduğu kayıt türü.
 * Yeni bir modül denetlenen bir işlem eklediğinde kendi kayıt türünü buraya eklemelidir.
 */
public enum AuditResourceType {
    STAFF_ACCOUNT,
    PATIENT,
    /** Elektronik tıbbi kayıt (muayene notu, öykü vb.; Sprint 3 modülleri). */
    MEDICAL_RECORD,
    /** Randevu; iptal (cancel) işlemleri için (US-12, Sprint 3). */
    APPOINTMENT
}
