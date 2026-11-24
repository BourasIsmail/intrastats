package ma.entraide.intranet.common;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Erreur métier convertie en réponse HTTP par {@link GlobalExceptionHandler}.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> details;

    public BusinessException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public BusinessException(HttpStatus status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException invalid(String message, List<String> details) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, message, details);
    }

    public HttpStatus status() {
        return status;
    }

    public List<String> details() {
        return details;
    }
}
