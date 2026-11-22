package org.hmscommunity.security.audit;

/**
 * Denetim kaydının ilgili olduğu kayıt türü; değerler {@code audit_entity_types.code} ile aynıdır.
 */
public enum AuditResourceType {
    PATIENT,
    /**
     * Elektronik tıbbi kayıt (Sprint 3 modülleri). Veritabanında bu kayıt türü henüz tanımlı değildir;
     * EMR migration'ı {@code audit_entity_types} tablosuna eklemeden yazılan olay {@code HL002} ile reddedilir.
     */
    MEDICAL_RECORD
}
