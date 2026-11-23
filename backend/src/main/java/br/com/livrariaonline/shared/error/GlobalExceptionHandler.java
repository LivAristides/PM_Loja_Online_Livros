package br.com.livrariaonline.shared.error;

import br.com.livrariaonline.identity.AccountRecoveryService;
import br.com.livrariaonline.identity.AdminInvitationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiErrorWriter errors;
    public GlobalExceptionHandler(ApiErrorWriter errors) { this.errors = errors; }

    @ExceptionHandler({AccountRecoveryService.InvalidRecoveryTokenException.class, AdminInvitationService.InvalidInvitationException.class})
    public ResponseEntity<ApiError> handleInvalidToken(RuntimeException exception) {
        ApiError error = errors.error(HttpStatus.BAD_REQUEST.value(), "invalid_or_expired_token", "O link informado é inválido ou expirou.");
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidInput(IllegalArgumentException exception) {
        ApiError error = errors.error(HttpStatus.BAD_REQUEST.value(), "invalid_request", exception.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(errors.error(HttpStatus.BAD_REQUEST.value(), "validation_failed", "Há campos inválidos.", details));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(errors.error(HttpStatus.BAD_REQUEST.value(), "invalid_request", "A solicitação é inválida."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        ApiError error = errors.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal_error", "Ocorreu um erro inesperado.");
        LOG.error("Unhandled API error: type={} correlationId={}", exception.getClass().getSimpleName(), error.correlationId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
