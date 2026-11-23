package br.com.livrariaonline.shared.error;

import br.com.livrariaonline.shared.correlation.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;
    public ApiErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ApiError error(int status, String code, String message) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) correlationId = UUID.randomUUID().toString();
        return new ApiError(Instant.now(), status, code, message, correlationId, List.of());
    }

    public ApiError error(int status, String code, String message, List<ApiError.FieldViolation> details) {
        ApiError base = error(status, code, message);
        return new ApiError(base.timestamp(), base.status(), base.code(), base.message(), base.correlationId(), details);
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        ApiError error = error(status, code, message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER_NAME, error.correlationId());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
