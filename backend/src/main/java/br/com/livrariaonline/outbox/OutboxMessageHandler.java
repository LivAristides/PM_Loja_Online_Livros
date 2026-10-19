package br.com.livrariaonline.outbox;

public interface OutboxMessageHandler {
    boolean supports(String messageType);

    void handle(OutboxMessage message);
}
