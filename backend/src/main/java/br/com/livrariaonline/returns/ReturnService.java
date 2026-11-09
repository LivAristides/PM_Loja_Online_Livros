package br.com.livrariaonline.returns;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReturnService {
    private final ReturnRepository repository;
    private final AuditLogService audit;

    ReturnService(ReturnRepository repository, AuditLogService audit) { this.repository = repository; this.audit = audit; }

    @Transactional
    ReturnRequest request(String customerEmail, UUID orderId, ReturnReason reason) {
        ReturnRepository.DeliveredOrder order = repository.findDeliveredOrderForUpdate(customerEmail, orderId);
        if (!"DELIVERED".equals(order.status()) || order.deliveredAt() == null) throw new ReturnUnavailableException();
        Instant requestedAt = Instant.now();
        if (requestedAt.isAfter(order.deliveredAt().plusSeconds(reason == ReturnReason.WITHDRAWAL ? 7 * 86_400L : 30 * 86_400L))) {
            throw new ReturnDeadlineExpiredException();
        }
        UUID returnId = UUID.randomUUID();
        repository.create(returnId, orderId, reason, addBusinessDays(requestedAt, 2));
        repository.transitionContext("CUSTOMER", customerEmail, "RETURN_REQUESTED_" + reason.name());
        repository.markOrderReturnRequested(orderId);
        audit.record(new AuditEvent("CUSTOMER", customerEmail, "RETURN_REQUESTED", "RETURN_REQUEST", returnId.toString(), UUID.randomUUID(),
                "{\"reason\":\"" + reason.name() + "\"}"));
        return repository.findOwned(customerEmail, returnId);
    }

    @Transactional
    ReturnRequest decide(String actor, UUID returnId, ReturnStatus decision, String suppliedJustification) {
        ReturnRequest request = repository.findForUpdate(returnId);
        if (request.status() != ReturnStatus.REQUESTED || (decision != ReturnStatus.APPROVED && decision != ReturnStatus.REFUSED)) {
            throw new InvalidReturnTransitionException();
        }
        if (decision == ReturnStatus.REFUSED) {
            String justification = requiredJustification(suppliedJustification);
            repository.refuse(returnId, justification);
            audit.record(new AuditEvent("SUPPORT", actor, "RETURN_REFUSED", "RETURN_REQUEST", returnId.toString(), UUID.randomUUID(),
                    "{\"justificationProvided\":true}"));
        } else {
            Instant approvedAt = Instant.now();
            repository.approve(returnId, approvedAt, approvedAt.plusSeconds(7 * 86_400L));
            audit.record(new AuditEvent("SUPPORT", actor, "RETURN_APPROVED", "RETURN_REQUEST", returnId.toString(), UUID.randomUUID(), "{}"));
        }
        return repository.findForUpdate(returnId);
    }

    @Transactional
    ReturnRequest post(String customerEmail, UUID returnId, String suppliedTrackingCode) {
        ReturnRequest request = repository.findOwned(customerEmail, returnId);
        if (request.status() != ReturnStatus.APPROVED || Instant.now().isAfter(request.postingDueAt())) throw new PostingUnavailableException();
        String trackingCode = normalizeTrackingCode(suppliedTrackingCode);
        repository.post(returnId, trackingCode);
        audit.record(new AuditEvent("CUSTOMER", customerEmail, "RETURN_POSTED", "RETURN_REQUEST", returnId.toString(), UUID.randomUUID(), "{\"simulated\":true}"));
        return repository.findOwned(customerEmail, returnId);
    }

    ReturnRequest detail(String customerEmail, UUID returnId) { return repository.findOwned(customerEmail, returnId); }

    private Instant addBusinessDays(Instant instant, int days) {
        ZonedDateTime date = instant.atZone(ZoneOffset.UTC); int added = 0;
        while (added < days) { date = date.plusDays(1); if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) added++; }
        return date.toInstant();
    }
    private String requiredJustification(String value) { if (value == null || value.isBlank()) throw new RefusalJustificationRequiredException(); return value.trim(); }
    private String normalizeTrackingCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9-]{6,80}")) throw new InvalidTrackingCodeException();
        return normalized;
    }
    static final class ReturnUnavailableException extends IllegalArgumentException { ReturnUnavailableException() { super("O pedido deve estar entregue para solicitar devolução."); } }
    static final class ReturnDeadlineExpiredException extends IllegalArgumentException { ReturnDeadlineExpiredException() { super("O prazo para solicitar devolução expirou."); } }
    static final class InvalidReturnTransitionException extends IllegalArgumentException { InvalidReturnTransitionException() { super("Transição de devolução inválida."); } }
    static final class RefusalJustificationRequiredException extends IllegalArgumentException { RefusalJustificationRequiredException() { super("A recusa exige justificativa."); } }
    static final class PostingUnavailableException extends IllegalArgumentException { PostingUnavailableException() { super("A postagem não está disponível para esta devolução."); } }
    static final class InvalidTrackingCodeException extends IllegalArgumentException { InvalidTrackingCodeException() { super("Código de rastreio de devolução inválido."); } }
}
