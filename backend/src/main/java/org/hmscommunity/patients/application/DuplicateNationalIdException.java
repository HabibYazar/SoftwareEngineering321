package org.hmscommunity.patients.application;

import org.hmscommunity.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** US-06: aynı ulusal kimlik numarasıyla ikinci bir hasta kaydedilemez (HTTP 409). */
public class DuplicateNationalIdException extends ApiException {

    public DuplicateNationalIdException() {
        super(HttpStatus.CONFLICT, "DUPLICATE_NATIONAL_ID", "Patient already registered",
            "A patient with this national ID is already registered.");
    }
}
