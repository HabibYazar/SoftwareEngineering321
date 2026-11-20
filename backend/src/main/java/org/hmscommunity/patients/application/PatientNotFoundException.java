package org.hmscommunity.patients.application;

import org.hmscommunity.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** İstenen hasta kaydı bulunamadı (HTTP 404). */
public class PatientNotFoundException extends ApiException {

    public PatientNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "Patient not found", "No patient exists with this id.");
    }
}
