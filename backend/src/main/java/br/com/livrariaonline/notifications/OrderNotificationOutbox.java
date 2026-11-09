package br.com.livrariaonline.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.livrariaonline.outbox.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderNotificationOutbox {
    static final String MESSAGE_TYPE = "ORDER_NOTIFICATION";
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OrderNotificationOutbox(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void publish(UUID orderId, OrderNotificationType eventType) {
        try {
            outbox.enqueue(MESSAGE_TYPE, objectMapper.writeValueAsString(new NotificationPayload(orderId, eventType)), Instant.now());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao registrar notificação do pedido.", exception);
        }
    }

    record NotificationPayload(UUID orderId, OrderNotificationType eventType) { }
}
