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
                SELECT id, name, email, password_hash, enabled, failed_login_attempts, locked_until, password_change_required
                FROM app_user WHERE email = ?
                """, (resultSet, rowNumber) -> mapUser(resultSet), normalizeEmail(email));
        return users.stream().findFirst();
    }

    public AppUser createCustomer(String name, String email, String passwordHash) {
        return createUser(name, email, passwordHash, Role.CUSTOMER, false);
    }

    public AppUser createUser(String name, String email, String passwordHash, Role role) {
        return createUser(name, email, passwordHash, role, false);
    }

    public AppUser createUser(String name, String email, String passwordHash, Role role, boolean passwordChangeRequired) {
        UUID id = UUID.randomUUID();
        String normalizedEmail = normalizeEmail(email);
        jdbcTemplate.update("""
                INSERT INTO app_user (id, name, email, password_hash, password_change_required)
                VALUES (?, ?, ?, ?, ?)
                """, id, name.trim(), normalizedEmail, passwordHash, passwordChangeRequired);
        jdbcTemplate.update("INSERT INTO app_user_role (user_id, role) VALUES (?, ?)", id, role.name());
        return findByEmail(normalizedEmail).orElseThrow();
    }

    public void resetFailedLoginAttempts(UUID userId) {
        jdbcTemplate.update("""
                UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, userId);
    }

    public void recordTermsConsent(UUID userId, String version) {
        jdbcTemplate.update("""
                INSERT INTO user_consent (id, user_id, consent_type, version, granted)
                VALUES (?, ?, 'TERMS', ?, TRUE)
                """, UUID.randomUUID(), userId, version);
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

    public List<AppUser> administrativeUsers() {
        return jdbcTemplate.query("""
                SELECT id, name, email, password_hash, enabled, failed_login_attempts, locked_until, password_change_required
                FROM app_user WHERE id IN (SELECT user_id FROM app_user_role WHERE role <> 'CUSTOMER') ORDER BY email
                """, (resultSet, rowNumber) -> mapUser(resultSet));
    }

    public void deactivateAdministrativeUser(UUID userId) {
        jdbcTemplate.update("UPDATE app_user SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND EXISTS (SELECT 1 FROM app_user_role WHERE user_id = ? AND role <> 'CUSTOMER')", userId, userId);
        jdbcTemplate.update("DELETE FROM spring_session WHERE principal_name = (SELECT email FROM app_user WHERE id = ?)", userId);
    }

    public void completeInitialPasswordChange(String email, String passwordHash) {
        jdbcTemplate.update("UPDATE app_user SET password_hash = ?, password_change_required = FALSE, updated_at = CURRENT_TIMESTAMP WHERE email = ? AND password_change_required = TRUE",
                passwordHash, normalizeEmail(email));
    }

    private AppUser mapUser(ResultSet resultSet) throws SQLException {
        UUID id = resultSet.getObject("id", UUID.class);
        Set<Role> roles = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT role FROM app_user_role WHERE user_id = ? ORDER BY role", String.class, id)
                .stream().map(Role::valueOf).toList());
        var lockedUntil = resultSet.getTimestamp("locked_until");
        return new AppUser(id, resultSet.getString("name"), resultSet.getString("email"),
                resultSet.getString("password_hash"), resultSet.getBoolean("enabled"),
                resultSet.getInt("failed_login_attempts"), lockedUntil == null ? null : lockedUntil.toInstant(), resultSet.getBoolean("password_change_required"), roles);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
