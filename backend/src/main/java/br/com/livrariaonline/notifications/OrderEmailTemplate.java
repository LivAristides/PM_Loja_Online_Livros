package br.com.livrariaonline.notifications;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class OrderEmailTemplate {
    EmailTemplate forEvent(OrderNotificationType eventType, UUID orderId) {
        String reference = orderId.toString();
        return switch (eventType) {
            case PAYMENT_CONFIRMED -> new EmailTemplate("Pagamento confirmado", "O pagamento do pedido " + reference + " foi confirmado.");
            case ORDER_CANCELLED -> new EmailTemplate("Pedido cancelado", "O pedido " + reference + " foi cancelado.");
            case ORDER_SHIPPED -> new EmailTemplate("Pedido enviado", "O pedido " + reference + " foi enviado e já está em trânsito.");
            case ORDER_DELIVERED -> new EmailTemplate("Pedido entregue", "O pedido " + reference + " foi marcado como entregue.");
        };
    }
}
