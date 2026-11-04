package br.com.livrariaonline.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import br.com.livrariaonline.outbox.OutboxRepository;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentService {
    private final PaymentRepository repository;
    private final SimulatedPaymentGateway gateway;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final OutboxRepository outboxRepository;

    PaymentService(PaymentRepository repository, SimulatedPaymentGateway gateway, ObjectMapper objectMapper,
                   OutboxRepository outboxRepository, @Value("${app.payments.webhook-secret}") String webhookSecret) {
        this.repository = repository; this.gateway = gateway; this.objectMapper = objectMapper; this.outboxRepository = outboxRepository; this.webhookSecret = webhookSecret;
    }

    @Transactional
    PaymentAttempt createAttempt(String email, UUID orderId, PaymentMethod method, SimulatedOutcome outcome) {
        repository.assertOrderOwner(orderId, email);
        if (method == PaymentMethod.CARD && repository.cardAttempts(orderId) >= 3) throw new PaymentRepository.CardAttemptLimitException();
        if (repository.hasProcessingAttempt(orderId)) throw new PaymentRepository.ProcessingPaymentException();
        PaymentStatus status = gateway.process(method, outcome);
        return repository.insert(orderId, method, status, gateway.transactionReference());
    }

    @Transactional
    boolean processWebhook(String payload, String signature) {
        if (!MessageDigest.isEqual(hmac(payload).getBytes(StandardCharsets.US_ASCII), safe(signature).getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidWebhookSignatureException();
        }
        WebhookPayload event = parse(payload);
        if (!repository.recordWebhook(event.eventId(), event.attemptId(), sha256(payload))) return false;
        PaymentAttempt attempt = repository.find(event.attemptId());
        if (attempt.status() == PaymentStatus.EXPIRED && event.status() == PaymentStatus.APPROVED) {
            if (repository.markLatePaymentForRefund(attempt.id())) {
                String compensation = payload(new PaymentCompensationHandler.LatePaymentPayload(attempt.id(), attempt.orderId()));
                outboxRepository.enqueue(PaymentCompensationHandler.REFUND_REQUEST, compensation, java.time.Instant.now());
                outboxRepository.enqueue(PaymentCompensationHandler.LATE_PAYMENT_ALERT, compensation, java.time.Instant.now());
            }
            return true;
        }
        if (attempt.status() != PaymentStatus.PROCESSING) throw new InvalidWebhookStatusException();
        if (event.status() != PaymentStatus.APPROVED && event.status() != PaymentStatus.DECLINED) throw new InvalidWebhookStatusException();
        repository.updateStatus(attempt.id(), event.status());
        return true;
    }

    private WebhookPayload parse(String payload) {
        try { return objectMapper.readValue(payload, WebhookPayload.class); }
        catch (JsonProcessingException exception) { throw new InvalidWebhookPayloadException(); }
    }
    private String payload(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Falha ao registrar compensação de pagamento.", exception); }
    }
    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Falha ao validar assinatura de webhook.", exception); }
    }
    private String sha256(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private String safe(String signature) { return signature == null ? "" : signature.trim().toLowerCase(); }

    static final class InvalidWebhookSignatureException extends IllegalArgumentException { InvalidWebhookSignatureException() { super("Assinatura de webhook inválida."); } }
    static final class InvalidWebhookPayloadException extends IllegalArgumentException { InvalidWebhookPayloadException() { super("Evento de webhook inválido."); } }
    static final class InvalidWebhookStatusException extends IllegalArgumentException { InvalidWebhookStatusException() { super("Transição de pagamento inválida."); } }
}
