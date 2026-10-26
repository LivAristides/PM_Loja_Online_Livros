package br.com.livrariaonline.outbox;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxProcessor.class);
    private final OutboxRepository outboxRepository;
    private final List<OutboxMessageHandler> handlers;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxProcessor(OutboxRepository outboxRepository, List<OutboxMessageHandler> handlers,
                           OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.handlers = handlers;
        this.batchSize = properties.batchSize();
        this.maxAttempts = properties.maxAttempts();
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-delay-ms}")
    public void processAvailableMessages() {
        for (OutboxMessage message : outboxRepository.claimAvailable(batchSize)) {
            process(message);
        }
    }

    void process(OutboxMessage message) {
        try {
            OutboxMessageHandler handler = handlers.stream().filter(candidate -> candidate.supports(message.messageType()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No handler registered for outbox message type"));
            handler.handle(message);
            outboxRepository.markCompleted(message.id());
        } catch (Exception exception) {
            LOGGER.warn("Outbox message processing failed: id={}, type={}", message.id(), message.messageType());
            outboxRepository.markFailed(message.id(), exception.getClass().getSimpleName(), maxAttempts);
        }
    }
}
