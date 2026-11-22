package org.hmscommunity.common;

import org.hmscommunity.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Başka bir iş akışına ait kalıcılık (persistence) entegrasyonu henüz sağlanmadığında fırlatılır.
 * Uygulama bu durumda güvenli tarafta kalır (fail-closed): sahte veri üretmez, token vermez,
 * HTTP 503 ile entegrasyonun beklendiğini bildirir.
 */
public class IntegrationPendingException extends ApiException {

    public IntegrationPendingException(String integration) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_PENDING", "Integration pending",
            "Required persistence integration is not available yet: " + integration + ".");
    }
}
