package br.com.livrariaonline.payments;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Local adapter only; it never receives or stores real card data. */
@Component
class SimulatedPaymentGateway {
    PaymentStatus process(PaymentMethod method, SimulatedOutcome outcome) {
        if (method == PaymentMethod.PIX && outcome == SimulatedOutcome.PROCESSING) return PaymentStatus.PENDING;
        return PaymentStatus.valueOf(outcome.name());
    }

    String transactionReference() { return "sim-" + UUID.randomUUID(); }
}
