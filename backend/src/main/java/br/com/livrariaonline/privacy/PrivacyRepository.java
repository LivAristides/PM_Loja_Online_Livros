package br.com.livrariaonline.privacy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PrivacyRepository {
    private final JdbcTemplate jdbc;

    PrivacyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    PrivacyExport exportFor(String email) {
        PrivacyProfile profile = jdbc.query("SELECT id, name, email, created_at FROM app_user WHERE email = ?", (rs, row) ->
                new PrivacyProfile(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"), rs.getTimestamp("created_at").toInstant()), email)
                .stream().findFirst().orElseThrow(PrivacyAccessDeniedException::new);
        List<PrivacyAddress> addresses = jdbc.query("""
                SELECT id, label, recipient_name, postal_code, street, number, complement, neighborhood, city, state
                FROM customer_address WHERE user_id = ? ORDER BY created_at
                """, (rs, row) -> new PrivacyAddress(rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("recipient_name"),
                rs.getString("postal_code"), rs.getString("street"), rs.getString("number"), rs.getString("complement"), rs.getString("neighborhood"),
                rs.getString("city"), rs.getString("state")), profile.id());
        List<PrivacyConsent> consents = jdbc.query("""
                SELECT consent_type, version, granted, recorded_at FROM user_consent WHERE user_id = ? ORDER BY recorded_at
                """, (rs, row) -> new PrivacyConsent(rs.getString("consent_type"), rs.getString("version"), rs.getBoolean("granted"),
                rs.getTimestamp("recorded_at").toInstant()), profile.id());
        List<PrivacyOrder> orders = jdbc.query("""
                SELECT id, status, merchandise_total, shipping_fee, total, shipping_zone_name, shipping_estimated_business_days, created_at
                FROM customer_order WHERE customer_id = ? ORDER BY created_at
                """, (rs, row) -> new PrivacyOrder(rs.getObject("id", UUID.class), rs.getString("status"), rs.getBigDecimal("merchandise_total"),
                rs.getBigDecimal("shipping_fee"), rs.getBigDecimal("total"), rs.getString("shipping_zone_name"),
                rs.getInt("shipping_estimated_business_days"), rs.getTimestamp("created_at").toInstant()), profile.id());
        return new PrivacyExport(profile, addresses, consents, orders);
    }

    boolean hasActiveOrder(String email) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id
                WHERE u.email = ? AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'RETURNED')
                """, Integer.class, email);
        return count != null && count > 0;
    }

    UUID userId(String email) {
        return jdbc.query("SELECT id FROM app_user WHERE email = ?", (rs, row) -> rs.getObject(1, UUID.class), email).stream()
                .findFirst().orElseThrow(PrivacyAccessDeniedException::new);
    }

    void anonymize(UUID userId, String email, String anonymizedEmail, String anonymizedPasswordHash) {
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", email);
        jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_consent WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM customer_address WHERE user_id = ?", userId);
        jdbc.update("""
                UPDATE customer_order SET customer_name = 'Titular anonimizado', customer_email = ?
                WHERE customer_id = ?
                """, anonymizedEmail, userId);
        jdbc.update("""
                UPDATE customer_order_shipping_address address SET recipient_name = 'Titular anonimizado', postal_code = '00000000',
                    street = 'Dados removidos', number = 'S/N', complement = NULL, neighborhood = 'Dados removidos', city = 'Dados removidos', state = 'NA'
                WHERE address.order_id IN (SELECT id FROM customer_order WHERE customer_id = ?)
                """, userId);
        jdbc.update("UPDATE audit_log SET actor_reference = ? WHERE actor_reference = ?", userId.toString(), email);
        jdbc.update("UPDATE audit_log SET target_reference = ? WHERE target_reference = ?", userId.toString(), email);
        jdbc.update("""
                UPDATE app_user SET name = 'Titular anonimizado', email = ?, password_hash = ?, enabled = FALSE,
                    failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, anonymizedEmail, anonymizedPasswordHash, userId);
    }

    List<RetentionPolicy> retentionPolicies() {
        return jdbc.query("SELECT policy_key, retention_days FROM privacy_retention_policy ORDER BY policy_key",
                (rs, row) -> new RetentionPolicy(rs.getString("policy_key"), rs.getInt("retention_days")));
    }

    void updateRetention(String key, int days) {
        if (jdbc.update("UPDATE privacy_retention_policy SET retention_days = ?, updated_at = CURRENT_TIMESTAMP WHERE policy_key = ?", days, key) != 1) {
            throw new InvalidRetentionPolicyException();
        }
    }

    record PrivacyProfile(UUID id, String name, String email, java.time.Instant createdAt) { }
    record PrivacyAddress(UUID id, String label, String recipientName, String postalCode, String street, String number,
                          String complement, String neighborhood, String city, String state) { }
    record PrivacyConsent(String type, String version, boolean granted, java.time.Instant recordedAt) { }
    record PrivacyOrder(UUID id, String status, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total,
                        String shippingZoneName, int shippingEstimatedBusinessDays, java.time.Instant createdAt) { }
    record PrivacyExport(PrivacyProfile profile, List<PrivacyAddress> addresses, List<PrivacyConsent> consents, List<PrivacyOrder> orders) { }
    record RetentionPolicy(String key, int retentionDays) { }
    static final class PrivacyAccessDeniedException extends IllegalArgumentException { PrivacyAccessDeniedException() { super("Conta não encontrada."); } }
    static final class ActiveOrderAnonymizationException extends IllegalArgumentException { ActiveOrderAnonymizationException() { super("A anonimização não é permitida enquanto houver pedido em andamento."); } }
    static final class InvalidRetentionPolicyException extends IllegalArgumentException { InvalidRetentionPolicyException() { super("Política de retenção inválida."); } }
}
