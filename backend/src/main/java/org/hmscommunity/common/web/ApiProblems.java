package org.hmscommunity.common.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * OpenAPI'deki {@code Problem} şemasına (title, status, code, detail, errors) uygun
 * {@link ProblemDetail} nesneleri üreten yardımcı sınıf.
 */
public final class ApiProblems {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    private ApiProblems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(CODE, code);
        return problem;
    }

    public static ProblemDetail validation(List<FieldViolation> violations) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Validation failed",
            "One or more fields are missing or invalid.");
        problem.setProperty(ERRORS, List.copyOf(violations));
        return problem;
    }

    /**
     * Sözleşme her problem yanıtında {@code code} alanını zorunlu tuttuğu için,
     * Spring'in ürettiği standart hatalara durum adından türetilmiş bir kod eklenir.
     */
    public static void ensureCode(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            problem.setProperty(CODE, status != null ? status.name() : "HTTP_" + problem.getStatus());
        }
    }
}
