package br.com.livrariaonline.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(long pollingDelayMs, int batchSize, int maxAttempts) {
}
