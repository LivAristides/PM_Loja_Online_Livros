package br.com.livrariaonline.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void record(AuditEvent event) {
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, actor_type, actor_reference, action, target_type, target_reference,
                    correlation_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), event.actorType(), event.actorReference(), event.action(), event.targetType(),
                event.targetReference(), event.correlationId(), event.detailsJson());
    }
}
