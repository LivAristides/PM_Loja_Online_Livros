package br.com.livrariaonline.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.livrariaonline.outbox.OutboxMessage;
import br.com.livrariaonline.outbox.OutboxMessageHandler;
import org.springframework.stereotype.Component;

@Component
class OrderNotificationHandler implements OutboxMessageHandler {
    private final ObjectMapper objectMapper;
    private final NotificationRepository repository;
    private final SimulatedEmailGateway gateway;
    private final OrderEmailTemplate template;

    OrderNotificationHandler(ObjectMapper objectMapper, NotificationRepository repository, SimulatedEmailGateway gateway, OrderEmailTemplate template) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.gateway = gateway;
        this.template = template;
    }

    @Override
    public boolean supports(String messageType) { return OrderNotificationOutbox.MESSAGE_TYPE.equals(messageType); }

    @Override
    public void handle(OutboxMessage message) {
        OrderNotificationOutbox.NotificationPayload payload = parse(message);
        String recipient = repository.recipientFor(payload.orderId());
        gateway.send(message.id(), payload.orderId(), recipient, template.forEvent(payload.eventType(), payload.orderId()));
    }

    private OrderNotificationOutbox.NotificationPayload parse(OutboxMessage message) {
        try { return objectMapper.readValue(message.payloadJson(), OrderNotificationOutbox.NotificationPayload.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Mensagem de notificação inválida."); }
    }
}
