package org.hmscommunity.common.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tüm REST hatalarını OpenAPI {@code Problem} biçimine (application/problem+json) çevirir.
 * Reddedilen değerler ve iç hata ayrıntıları istemciye asla yansıtılmaz.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = ex instanceof RequestValidationException validation
            ? ApiProblems.validation(validation.violations())
            : ApiProblems.of(ex.status(), ex.code(), ex.title(), ex.getMessage());
        if (ex.status().is5xxServerError()) {
            log.warn("Request failed with {} {}: {}", ex.status().value(), ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) throws Exception {
        // Güvenlik istisnaları Spring Security'nin kendi 401/403 işleyicilerine bırakılır.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unexpected server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiProblems.of(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error",
            "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
            .map(error -> new FieldViolation(
                error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                error.getCode(),
                error.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(ApiProblems.validation(violations));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Tip/biçim uyuşmazlığında hangi alanın hatalı olduğu bildirilir; Jackson mesajı
        // reddedilen değeri içerdiğinden istemciye aktarılmaz.
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String message = "has an invalid format";
            if (mismatch instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                    && invalid.getTargetType().isEnum()) {
                message = "must be one of " + Arrays.toString(invalid.getTargetType().getEnumConstants());
            }
            FieldViolation violation = new FieldViolation(fieldPath(mismatch.getPath()), "InvalidFormat", message);
            return ResponseEntity.badRequest().body(ApiProblems.validation(List.of(violation)));
        }
        return ResponseEntity.badRequest().body(ApiProblems.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
            "Malformed request", "Request body is missing or is not valid JSON."));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "request";
        FieldViolation violation = new FieldViolation(field, "InvalidFormat", "has an invalid format");
        return ResponseEntity.badRequest().body(ApiProblems.validation(List.of(violation)));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ApiProblems.ensureCode(problem);
        }
        return response;
    }

    private static String fieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            }
        }
        return field.isEmpty() ? "request" : field.toString();
    }
}
