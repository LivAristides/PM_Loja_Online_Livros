package br.com.livrariaonline.shared.error;

import br.com.livrariaonline.shared.correlation.CorrelationIdFilter;
import br.com.livrariaonline.identity.AccountRecoveryService;
import br.com.livrariaonline.identity.AdminInvitationService;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccountRecoveryService.InvalidRecoveryTokenException.class, AdminInvitationService.InvalidInvitationException.class})
    public ResponseEntity<ApiError> handleInvalidToken(RuntimeException exception) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiError error = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "invalid_or_expired_token",
                "O link informado é inválido ou expirou.", correlationId);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidInput(IllegalArgumentException exception) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiError error = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "invalid_request",
                exception.getMessage(), correlationId);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiError error = new ApiError(Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "internal_error",
                "Ocorreu um erro inesperado.", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
