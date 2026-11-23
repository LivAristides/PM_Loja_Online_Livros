package br.com.livrariaonline.returns;

import java.time.Instant;
import java.util.UUID;

enum ReturnReason {
    WITHDRAWAL, DEFECTIVE, WRONG_ITEM
}

enum ReturnStatus {
    REQUESTED, APPROVED, REFUSED, POSTED, RECEIVED
}

enum ReturnReceiptDisposition { RESTOCK, DAMAGED }

record ReturnRequest(UUID id, UUID orderId, ReturnReason reason, ReturnStatus status, Instant requestedAt, Instant reviewDueAt,
                     Instant approvedAt, String refusalJustification, Instant postingDueAt, Instant postedAt, String returnTrackingCode,
                     Instant receivedAt, ReturnReceiptDisposition receiptDisposition, UUID refundPaymentAttemptId) { }
