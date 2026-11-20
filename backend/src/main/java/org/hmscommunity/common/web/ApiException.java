package org.hmscommunity.common.web;

import org.springframework.http.HttpStatus;

/**
 * Modüllerin, HTTP sözleşmesindeki (OpenAPI {@code Problem}) bir hata yanıtına
 * dönüşmesi gereken iş hatalarını fırlatmak için kullandığı ortak temel sınıf.
 * Mesajlar istemciye gösterildiği için hassas veri (parola, token, hasta bilgisi) içermemelidir.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    public ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
