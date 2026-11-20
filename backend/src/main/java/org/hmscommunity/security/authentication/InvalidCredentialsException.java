package org.hmscommunity.security.authentication;

import org.hmscommunity.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Hatalı kullanıcı adı veya parola. Hesabın var olup olmadığını ele vermemek için
 * her iki durumda da aynı genel mesaj kullanılır.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Authentication failed",
            "Invalid username or password.");
    }
}
