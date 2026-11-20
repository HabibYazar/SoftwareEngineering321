package org.hmscommunity.common.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Bir veya daha fazla alan düzeyindeki doğrulama hatasını tek seferde taşır (HTTP 400).
 */
public class RequestValidationException extends ApiException {

    private final transient List<FieldViolation> violations;

    public RequestValidationException(List<FieldViolation> violations) {
        super(HttpStatus.BAD_REQUEST, ApiProblems.VALIDATION_FAILED, "Validation failed",
            "One or more fields are missing or invalid.");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
