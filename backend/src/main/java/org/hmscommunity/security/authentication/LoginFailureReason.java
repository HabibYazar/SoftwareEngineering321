package org.hmscommunity.security.authentication;

/**
 * Başarısız giriş nedeni. Yalnızca denetim kaydına yazılır; istemci her durumda aynı genel
 * 401 yanıtını alır, böylece hesabın var olup olmadığı dışarıdan anlaşılamaz.
 */
public enum LoginFailureReason {
    /** Hesap var, parola yanlış. */
    BAD_CREDENTIALS,
    /** Bu kullanıcı adıyla hesap yok. */
    UNKNOWN_ACCOUNT
}
