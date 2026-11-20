package org.hmscommunity.security.audit;

/** #145 kapsamında denetim kaydı üretmesi zorunlu olan işlem sınıfları. */
public enum AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGOUT,
    CREATE,
    UPDATE,
    DELETE,
    CANCEL,
    /** Hasta kaydı veya elektronik tıbbi kayıt (EMR) görüntüleme. */
    VIEW
}
