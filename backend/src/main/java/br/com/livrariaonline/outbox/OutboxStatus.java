package br.com.livrariaonline.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
