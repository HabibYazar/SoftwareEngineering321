package org.hmscommunity.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hmscommunity.common.web.ApiException;
import org.hmscommunity.common.web.FieldViolation;
import org.hmscommunity.common.web.RequestValidationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.http.HttpStatus;

/**
 * Data iş akışının SQLSTATE hata sözleşmesini ({@code docs/database/README.md} §4) HTTP sözleşmesine çevirir.
 *
 * <p>Eşleme: {@code HV001} → 400 (alan hataları DETAIL JSON'undan), {@code 23505}/{@code 23503} → 409,
 * {@code HA403} → 403. {@code HA401} (aktör bağlamı eksik) istemci hatası değil backend hatası olduğundan
 * çevrilmez ve HTTP 500 olarak kalır. Modüle özgü durumlar (ör. çakışan ulusal kimlik numarası) adaptörde,
 * bu genel çeviriden önce ele alınır. Hata yanıtlarına reddedilen değerler hiçbir zaman eklenmez.
 */
public final class PostgresErrors {

    public static final String VALIDATION_FAILED = "HV001";
    public static final String UNIQUE_VIOLATION = "23505";
    public static final String FOREIGN_KEY_VIOLATION = "23503";
    public static final String PERMISSION_DENIED = "HA403";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> FIELD_ERRORS = new TypeReference<>() {
    };

    /** Veritabanının {@code hms_field_errors} çıktısındaki tek bir alan hatası. */
    public record DatabaseFieldError(String field, String column, String error, String messageEn) {
    }

    private PostgresErrors() {
    }

    /** İstisna zincirindeki PostgreSQL sunucu hatası (SQLSTATE, DETAIL, kısıt adı vb.). */
    public static Optional<ServerErrorMessage> serverError(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return Optional.of(psql.getServerErrorMessage());
            }
        }
        return Optional.empty();
    }

    public static boolean hasSqlState(Throwable failure, String sqlState) {
        return serverError(failure).map(ServerErrorMessage::getSQLState).filter(sqlState::equals).isPresent();
    }

    /** {@code HV001} hatasının DETAIL alanındaki JSON listesini okur; okunamazsa boş liste döner. */
    public static List<DatabaseFieldError> fieldErrors(ServerErrorMessage message) {
        if (message.getDetail() == null) {
            return List.of();
        }
        try {
            return JSON.readValue(message.getDetail(), FIELD_ERRORS).stream()
                .map(error -> new DatabaseFieldError(text(error, "field"), text(error, "column"),
                    text(error, "error"), text(error, "messageEn")))
                .toList();
        } catch (Exception unreadableDetail) {
            return List.of();
        }
    }

    /**
     * Genel çeviri. Sözleşmede karşılığı olan bir SQLSTATE ise {@link ApiException} döndürür;
     * değilse boş döner ve çağıran özgün istisnayı yeniden fırlatır.
     */
    public static Optional<ApiException> translate(Throwable failure) {
        Optional<ServerErrorMessage> error = serverError(failure);
        if (error.isEmpty()) {
            return Optional.empty();
        }
        ServerErrorMessage message = error.get();
        return Optional.ofNullable(switch (String.valueOf(message.getSQLState())) {
            case VALIDATION_FAILED -> new RequestValidationException(fieldErrors(message).stream()
                .map(field -> new FieldViolation(field.field(), field.error(), field.messageEn()))
                .toList());
            case UNIQUE_VIOLATION, FOREIGN_KEY_VIOLATION -> new ApiException(HttpStatus.CONFLICT, "CONFLICT",
                "Conflict", "The request conflicts with the current state of another record.");
            case PERMISSION_DENIED -> new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied",
                "Your role is not permitted to perform this action.");
            default -> null;
        });
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}
