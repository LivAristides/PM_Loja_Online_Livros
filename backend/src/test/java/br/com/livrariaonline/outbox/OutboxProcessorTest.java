package br.com.livrariaonline.outbox;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxMessageHandler outboxMessageHandler;

    @Test
    void marksMessageCompletedAfterSuccessfulHandling() {
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "test.success", "{}", OutboxStatus.PROCESSING,
                1, Instant.now());
        org.mockito.Mockito.when(outboxMessageHandler.supports("test.success")).thenReturn(true);
        OutboxProcessor processor = new OutboxProcessor(outboxRepository, List.of(outboxMessageHandler),
                new OutboxProperties(5000, 20, 3));

        processor.process(message);

        verify(outboxMessageHandler).handle(message);
        verify(outboxRepository).markCompleted(message.id());
    }

    @Test
    void recordsFailureWhenNoHandlerSupportsTheMessage() {
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "test.unsupported", "{}",
                OutboxStatus.PROCESSING, 1, Instant.now());
        org.mockito.Mockito.when(outboxMessageHandler.supports("test.unsupported")).thenReturn(false);
        OutboxProcessor processor = new OutboxProcessor(outboxRepository, List.of(outboxMessageHandler),
                new OutboxProperties(5000, 20, 3));

        processor.process(message);

        verify(outboxRepository).markFailed(message.id(), "IllegalStateException", 3);
    }
}
