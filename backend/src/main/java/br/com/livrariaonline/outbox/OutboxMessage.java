package br.com.livrariaonline.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(UUID id, String messageType, String payloadJson, OutboxStatus status, int attempts,
                            Instant availableAt) {
}
