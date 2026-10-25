package br.com.livrariaonline.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate; private final IdentityRepository identityRepository; private final PasswordEncoder passwordEncoder;
    public AdminInvitationService(JdbcTemplate jdbcTemplate, IdentityRepository identityRepository, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate; this.identityRepository = identityRepository; this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public String invite(String email, Role role, String creator) {
        if (role == Role.CUSTOMER || role == Role.GENERAL_ADMINISTRATOR) throw new IllegalArgumentException("Role cannot be invited");
        String token = token();
        jdbcTemplate.update("UPDATE admin_invitation SET accepted_at = CURRENT_TIMESTAMP WHERE email = ? AND accepted_at IS NULL", normalize(email));
        jdbcTemplate.update("""
                INSERT INTO admin_invitation (id, email, role, token_hash, expires_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), normalize(email), role.name(), hash(token), java.sql.Timestamp.from(Instant.now().plusSeconds(1800)), creator);
        return token;
    }
    @Transactional
    public void accept(String token, String name, String password) {
        Invitation invitation = jdbcTemplate.query("""
                SELECT email, role FROM admin_invitation WHERE token_hash = ? AND accepted_at IS NULL
                AND expires_at > CURRENT_TIMESTAMP FOR UPDATE
                """, rs -> rs.next() ? new Invitation(rs.getString(1), Role.valueOf(rs.getString(2))) : null, hash(token));
        if (invitation == null) throw new InvalidInvitationException();
        identityRepository.createUser(name, invitation.email(), passwordEncoder.encode(password), invitation.role());
        jdbcTemplate.update("UPDATE admin_invitation SET accepted_at = CURRENT_TIMESTAMP WHERE token_hash = ?", hash(token));
    }
    private String token() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String token) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private record Invitation(String email, Role role) { }
    public static class InvalidInvitationException extends RuntimeException { }
}
