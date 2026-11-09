package br.com.livrariaonline.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.livrariaonline.outbox.OutboxMessage;
import br.com.livrariaonline.outbox.OutboxMessageHandler;
import org.springframework.stereotype.Component;

@Component
class PaymentCompensationHandler implements OutboxMessageHandler {
    static final String EXPIRE_RESERVATION = "ORDER_RESERVATION_EXPIRE";
    static final String REFUND_REQUEST = "PAYMENT_REFUND_REQUEST";
    static final String LATE_PAYMENT_ALERT = "FINANCE_PAYMENT_LATE_ALERT";

    private final PaymentRepository repository;
    private final ObjectMapper objectMapper;

    PaymentCompensationHandler(PaymentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository; this.objectMapper = objectMapper;
    }

    public boolean supports(String messageType) {
        return EXPIRE_RESERVATION.equals(messageType) || REFUND_REQUEST.equals(messageType) || LATE_PAYMENT_ALERT.equals(messageType);
    }

    public void handle(OutboxMessage message) {
        if (EXPIRE_RESERVATION.equals(message.messageType())) repository.expireReservation(parse(message, ReservationExpirationScheduler.ExpirationPayload.class).orderId());
        else if (REFUND_REQUEST.equals(message.messageType())) repository.completeRefund(parse(message, LatePaymentPayload.class).attemptId());
        // The persistent alert itself is the finance trace in this MVP; notifications are introduced in TASK-019.
    }

    private <T> T parse(OutboxMessage message, Class<T> type) {
        try { return objectMapper.readValue(message.payloadJson(), type); }
        catch (Exception exception) { throw new IllegalArgumentException("Mensagem de compensação inválida."); }
    }

    record LatePaymentPayload(java.util.UUID attemptId, java.util.UUID orderId) { }
}
