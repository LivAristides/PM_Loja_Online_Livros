package br.com.livrariaonline.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String code, String message, String correlationId, List<FieldViolation> details) {
    public record FieldViolation(String field, String message) { }
}
