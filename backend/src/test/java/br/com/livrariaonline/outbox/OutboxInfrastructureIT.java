package br.com.livrariaonline.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OutboxInfrastructureIT {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void persistsPendingMessageForProcessingAfterAnApplicationRestart() {
        OutboxMessage enqueued = outboxRepository.enqueue("test.persist", "{\"reference\":\"safe-demo\"}", Instant.now());

        OutboxMessage reloaded = outboxRepository.findById(enqueued.id());

        assertThat(reloaded.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.payloadJson()).contains("safe-demo");
    }

    @Test
    void recordsAProcessingFailureForRetry() {
        OutboxMessage message = outboxRepository.enqueue("test.failure", "{}", Instant.now());
        OutboxMessage claimed = outboxRepository.claimAvailable(10).stream()
                .filter(candidate -> candidate.id().equals(message.id()))
                .findFirst()
                .orElseThrow();

        outboxRepository.markFailed(claimed.id(), "simulated_failure", 3);

        OutboxMessage retriable = outboxRepository.findById(message.id());
        String error = jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_message WHERE id = ?", String.class, message.id());
        assertThat(retriable.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retriable.attempts()).isEqualTo(1);
        assertThat(error).isEqualTo("simulated_failure");
    }

    @Test
    void writesBasicAuditEventsWithoutSensitivePayloads() {
        UUID correlationId = UUID.randomUUID();
        auditLogService.record(new AuditEvent("SYSTEM", null, "OUTBOX_FAILURE_RECORDED", "OUTBOX_MESSAGE",
                null, correlationId, "{}"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE correlation_id = ?", Integer.class, correlationId);
        assertThat(count).isEqualTo(1);
    }
}
