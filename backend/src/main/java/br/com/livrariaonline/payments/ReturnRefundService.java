package br.com.livrariaonline.payments;

import br.com.livrariaonline.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReturnRefundService {
    private final PaymentRepository repository;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    ReturnRefundService(PaymentRepository repository, OutboxRepository outbox, ObjectMapper objectMapper) {
        this.repository = repository;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public UUID requestFullRefund(UUID orderId) {
        UUID attemptId = repository.markApprovedPaymentForRefund(orderId)
                .orElseThrow(NoApprovedPaymentForRefundException::new);
        try {
            String payload = objectMapper.writeValueAsString(new PaymentCompensationHandler.LatePaymentPayload(attemptId, orderId));
            outbox.enqueue(PaymentCompensationHandler.REFUND_REQUEST, payload, Instant.now());
            return attemptId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao solicitar estorno da devolução.", exception);
        }
    }

    public static final class NoApprovedPaymentForRefundException extends IllegalArgumentException {
        NoApprovedPaymentForRefundException() { super("Não há pagamento aprovado disponível para estorno."); }
    }
}
