package br.com.livrariaonline.payments;

import java.time.Instant;
import java.util.UUID;

enum PaymentMethod { PIX, CARD }
enum PaymentStatus { PENDING, PROCESSING, APPROVED, DECLINED, EXPIRED, CANCELLED, REFUND_PENDING, REFUNDED, REFUND_FAILED }
enum SimulatedOutcome { APPROVED, DECLINED, PROCESSING }
record PaymentAttempt(UUID id, UUID orderId, PaymentMethod method, PaymentStatus status, String providerTransactionId, Instant createdAt) { }
record WebhookPayload(UUID eventId, UUID attemptId, PaymentStatus status) { }
