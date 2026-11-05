package br.com.livrariaonline.notifications;

import java.util.UUID;

interface SimulatedEmailGateway {
    void send(UUID outboxMessageId, UUID orderId, String recipientEmail, EmailTemplate template);
}
