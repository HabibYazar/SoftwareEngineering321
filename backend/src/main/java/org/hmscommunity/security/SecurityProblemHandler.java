package org.hmscommunity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.hmscommunity.common.web.ApiProblems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Güvenlik filtre zincirinde oluşan reddetmeleri OpenAPI {@code Problem} biçiminde yazar.
 * Kimliği doğrulanmamış istekler için HTTP 401, kimliği doğrulanmış ancak rolü yetmeyen
 * istekler için HTTP 403 döndürür.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, ApiProblems.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "Authentication required", "A valid bearer token is required to access this resource."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, ApiProblems.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "Access denied", "Your role is not permitted to perform this action."));
    }

    void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem) throws IOException {
        try {
            problem.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException malformedPath) {
            // Bozuk bir yol, 401/403 yanıtını 500'e çevirmemeli; bu durumda instance alanı boş bırakılır.
        }
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
