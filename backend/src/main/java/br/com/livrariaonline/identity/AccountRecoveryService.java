package br.com.livrariaonline.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    public AccountRecoveryService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate; this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public String requestPasswordReset(String email) {
        UUID userId = jdbcTemplate.query("SELECT id FROM app_user WHERE email = ? AND enabled = TRUE", rs ->
                rs.next() ? rs.getObject(1, UUID.class) : null, email.toLowerCase());
        if (userId == null) return null;
        jdbcTemplate.update("UPDATE password_reset_token SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL", userId);
        String token = newToken();
        jdbcTemplate.update("INSERT INTO password_reset_token (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), userId, hash(token), java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
        return token;
    }
    @Transactional
    public void resetPassword(String token, String newPassword) {
        UUID userId = jdbcTemplate.query("""
                SELECT user_id FROM password_reset_token WHERE token_hash = ? AND used_at IS NULL
                AND expires_at > CURRENT_TIMESTAMP FOR UPDATE
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, hash(token));
        if (userId == null) throw new InvalidRecoveryTokenException();
        jdbcTemplate.update("UPDATE app_user SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", passwordEncoder.encode(newPassword), userId);
        jdbcTemplate.update("UPDATE password_reset_token SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL", userId);
        jdbcTemplate.update("DELETE FROM spring_session WHERE principal_name = (SELECT email FROM app_user WHERE id = ?)", userId);
    }
    private String newToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static class InvalidRecoveryTokenException extends RuntimeException { }
}
