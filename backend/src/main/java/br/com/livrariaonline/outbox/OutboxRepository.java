package br.com.livrariaonline.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository {
    private static final RowMapper<OutboxMessage> ROW_MAPPER = OutboxRepository::map;
    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public OutboxMessage enqueue(String messageType, String payloadJson, Instant availableAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_message (id, message_type, payload, status, available_at)
                VALUES (?, ?, CAST(? AS jsonb), 'PENDING', ?)
                """, id, messageType, payloadJson, Timestamp.from(availableAt));
        return new OutboxMessage(id, messageType, payloadJson, OutboxStatus.PENDING, 0, availableAt);
    }

    @Transactional
    public List<OutboxMessage> claimAvailable(int batchSize) {
        return jdbcTemplate.query("""
                WITH claimed AS (
                    SELECT id FROM outbox_message
                    WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_message message
                SET status = 'PROCESSING', attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
                FROM claimed
                WHERE message.id = claimed.id
                RETURNING message.id, message.message_type, message.payload::text, message.status,
                    message.attempts, message.available_at
                """, ROW_MAPPER, batchSize);
    }

    @Transactional
    public void markCompleted(UUID id) {
        jdbcTemplate.update("""
                UPDATE outbox_message
                SET status = 'COMPLETED', processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                    last_error = NULL
                WHERE id = ? AND status = 'PROCESSING'
                """, id);
    }

    @Transactional
    public void markFailed(UUID id, String error, int maxAttempts) {
        jdbcTemplate.update("""
                UPDATE outbox_message
                SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    available_at = CASE WHEN attempts >= ? THEN available_at
                        ELSE CURRENT_TIMESTAMP + INTERVAL '30 seconds' END,
                    last_error = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, maxAttempts, maxAttempts, sanitizeError(error), id);
    }

    public OutboxMessage findById(UUID id) {
        return jdbcTemplate.queryForObject("""
                SELECT id, message_type, payload::text, status, attempts, available_at
                FROM outbox_message WHERE id = ?
                """, ROW_MAPPER, id);
    }

    private static OutboxMessage map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxMessage(resultSet.getObject("id", UUID.class), resultSet.getString("message_type"),
                resultSet.getString("payload"), OutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempts"), resultSet.getTimestamp("available_at").toInstant());
    }

    private String sanitizeError(String error) {
        return error == null ? "processing_failed" : error.replaceAll("[\\r\\n]", " ").substring(0, Math.min(error.length(), 500));
    }
}
