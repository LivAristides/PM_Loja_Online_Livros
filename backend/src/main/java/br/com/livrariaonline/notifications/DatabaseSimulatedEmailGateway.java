package br.com.livrariaonline.notifications;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DatabaseSimulatedEmailGateway implements SimulatedEmailGateway {
    private final NotificationRepository repository;
    private final boolean forceFailure;

    DatabaseSimulatedEmailGateway(NotificationRepository repository, @Value("${app.notifications.force-failure:false}") boolean forceFailure) {
        this.repository = repository;
        this.forceFailure = forceFailure;
    }

    @Override
    public void send(UUID outboxMessageId, UUID orderId, String recipientEmail, EmailTemplate template) {
        if (forceFailure) throw new IllegalStateException("simulated_email_delivery_failure");
        repository.capture(outboxMessageId, orderId, recipientEmail, template);
    }
}
