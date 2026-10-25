package br.com.livrariaonline.payments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class PaymentController {
    private final PaymentService service;
    PaymentController(PaymentService service) { this.service = service; }

    @PostMapping("/payments/orders/{orderId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse create(@org.springframework.web.bind.annotation.PathVariable UUID orderId, @Valid @RequestBody AttemptInput input,
                           Authentication authentication) {
        return PaymentResponse.from(service.createAttempt(authentication.getName(), orderId, input.method(), input.simulatedOutcome()));
    }

    @PostMapping("/webhooks/payments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void webhook(@RequestBody String payload, @RequestHeader("X-Payment-Signature") String signature) { service.processWebhook(payload, signature); }

    record AttemptInput(@NotNull PaymentMethod method, @NotNull SimulatedOutcome simulatedOutcome) { }
    record PaymentResponse(UUID attemptId, UUID orderId, PaymentMethod method, PaymentStatus status, String transactionReference, Instant createdAt) {
        static PaymentResponse from(PaymentAttempt attempt) { return new PaymentResponse(attempt.id(), attempt.orderId(), attempt.method(), attempt.status(), attempt.providerTransactionId(), attempt.createdAt()); }
    }
}
