package br.com.livrariaonline.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private final JdbcTemplate jdbcTemplate;

    public IdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findByEmail(String email) {
        List<AppUser> users = jdbcTemplate.query("""
                SELECT id, name, email, password_hash, enabled, failed_login_attempts, locked_until
                FROM app_user WHERE email = ?
                """, (resultSet, rowNumber) -> mapUser(resultSet), normalizeEmail(email));
        return users.stream().findFirst();
    }

    public AppUser createCustomer(String name, String email, String passwordHash) {
        UUID id = UUID.randomUUID();
        String normalizedEmail = normalizeEmail(email);
        jdbcTemplate.update("""
                INSERT INTO app_user (id, name, email, password_hash)
                VALUES (?, ?, ?, ?)
                """, id, name.trim(), normalizedEmail, passwordHash);
        jdbcTemplate.update("INSERT INTO app_user_role (user_id, role) VALUES (?, 'CUSTOMER')", id);
        return findByEmail(normalizedEmail).orElseThrow();
    }

    public void resetFailedLoginAttempts(UUID userId) {
        jdbcTemplate.update("""
                UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, userId);
    }

    public void recordFailedLogin(String email) {
        jdbcTemplate.update("""
                UPDATE app_user
                SET failed_login_attempts = failed_login_attempts + 1,
                    locked_until = CASE WHEN failed_login_attempts + 1 >= 5
                        THEN CURRENT_TIMESTAMP + INTERVAL '15 minutes' ELSE locked_until END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE email = ? AND enabled = TRUE
                  AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
                """, normalizeEmail(email));
    }

    private AppUser mapUser(ResultSet resultSet) throws SQLException {
        UUID id = resultSet.getObject("id", UUID.class);
        Set<Role> roles = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT role FROM app_user_role WHERE user_id = ? ORDER BY role", String.class, id)
                .stream().map(Role::valueOf).toList());
        var lockedUntil = resultSet.getTimestamp("locked_until");
        return new AppUser(id, resultSet.getString("name"), resultSet.getString("email"),
                resultSet.getString("password_hash"), resultSet.getBoolean("enabled"),
                resultSet.getInt("failed_login_attempts"), lockedUntil == null ? null : lockedUntil.toInstant(), roles);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
