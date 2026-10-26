package br.com.livrariaonline.customers;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbcTemplate;
    public CustomerRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Profile findProfile(String email) {
        return jdbcTemplate.queryForObject("SELECT name, email FROM app_user WHERE email = ?", (rs, row) ->
                new Profile(rs.getString("name"), rs.getString("email")), email);
    }
    public void updateName(String email, String name) {
        jdbcTemplate.update("UPDATE app_user SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE email = ?", name.trim(), email);
    }
    public List<Address> findAddresses(String email) {
        return jdbcTemplate.query("""
                SELECT a.id, a.label, a.recipient_name, a.postal_code, a.street, a.number, a.complement,
                       a.neighborhood, a.city, a.state
                FROM customer_address a JOIN app_user u ON u.id = a.user_id WHERE u.email = ? ORDER BY a.created_at
                """, (rs, row) -> new Address(rs.getObject("id", UUID.class), rs.getString("label"),
                rs.getString("recipient_name"), rs.getString("postal_code"), rs.getString("street"),
                rs.getString("number"), rs.getString("complement"), rs.getString("neighborhood"),
                rs.getString("city"), rs.getString("state")), email);
    }
    public Address addAddress(String email, AddressInput input) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer_address (id, user_id, label, recipient_name, postal_code, street, number,
                    complement, neighborhood, city, state)
                SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM app_user WHERE email = ?
                """, id, input.label(), input.recipientName(), input.postalCode().replaceAll("\\D", ""), input.street(),
                input.number(), input.complement(), input.neighborhood(), input.city(), input.state().toUpperCase(), email);
        return findAddresses(email).stream().filter(address -> address.id().equals(id)).findFirst().orElseThrow();
    }
    public void deleteAddress(String email, UUID id) {
        jdbcTemplate.update("DELETE FROM customer_address WHERE id = ? AND user_id = (SELECT id FROM app_user WHERE email = ?)", id, email);
    }
    public void recordConsent(String email, String type, String version, boolean granted) {
        jdbcTemplate.update("""
                INSERT INTO user_consent (id, user_id, consent_type, version, granted)
                SELECT ?, id, ?, ?, ? FROM app_user WHERE email = ?
                """, UUID.randomUUID(), type, version, granted, email);
    }
    public boolean marketingGranted(String email) {
        Boolean granted = jdbcTemplate.query("""
                SELECT granted FROM user_consent c JOIN app_user u ON u.id = c.user_id
                WHERE u.email = ? AND consent_type = 'MARKETING' ORDER BY recorded_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getBoolean(1) : false, email);
        return Boolean.TRUE.equals(granted);
    }
    public record Profile(String name, String email) { }
    public record Address(UUID id, String label, String recipientName, String postalCode, String street, String number,
                          String complement, String neighborhood, String city, String state) { }
    public record AddressInput(String label, String recipientName, String postalCode, String street, String number,
                               String complement, String neighborhood, String city, String state) { }
}
