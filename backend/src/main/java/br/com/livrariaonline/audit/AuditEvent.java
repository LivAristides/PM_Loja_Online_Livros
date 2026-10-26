package br.com.livrariaonline.audit;

import java.util.UUID;

public record AuditEvent(String actorType, String actorReference, String action, String targetType,
                         String targetReference, UUID correlationId, String detailsJson) {
}
