package br.com.livrariaonline.shipping;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ShippingRepository {
    private final JdbcTemplate jdbc;
    ShippingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    StoreSettings settings() { return jdbc.queryForObject("SELECT store_name, contact_email, contact_phone FROM store_settings WHERE id = 1",
            (rs, row) -> new StoreSettings(rs.getString(1), rs.getString(2), rs.getString(3))); }
    void updateSettings(StoreSettings value) { jdbc.update("UPDATE store_settings SET store_name=?, contact_email=?, contact_phone=?, updated_at=CURRENT_TIMESTAMP WHERE id=1",
            value.storeName(), value.contactEmail(), value.contactPhone()); }
    ShippingZone createZone(String name, String start, String end, BigDecimal fee, int days) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO shipping_zone (id,name,postal_code_start,postal_code_end,fee,estimated_business_days) VALUES (?,?,?,?,?,?)",
                id, name, start, end, fee, days); return findZone(id);
    }
    List<ShippingZone> zones() { return jdbc.query("SELECT id,name,postal_code_start,postal_code_end,fee,estimated_business_days,active FROM shipping_zone ORDER BY postal_code_start", this::mapZone); }
    ShippingZone findZone(UUID id) { return jdbc.query("SELECT id,name,postal_code_start,postal_code_end,fee,estimated_business_days,active FROM shipping_zone WHERE id=?", this::mapZone, id).stream().findFirst().orElseThrow(); }
    void deactivateZone(UUID id) { jdbc.update("UPDATE shipping_zone SET active=FALSE, updated_at=CURRENT_TIMESTAMP WHERE id=?", id); }
    ShippingZone matchingZone(String postalCode) { return jdbc.query("""
            SELECT id,name,postal_code_start,postal_code_end,fee,estimated_business_days,active FROM shipping_zone
            WHERE active=TRUE AND postal_code_start <= ? AND postal_code_end >= ? ORDER BY postal_code_start LIMIT 1
            """, this::mapZone, postalCode, postalCode).stream().findFirst().orElseThrow(ShippingZoneUnavailableException::new); }
    private ShippingZone mapZone(java.sql.ResultSet rs, int row) throws java.sql.SQLException { return new ShippingZone(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("postal_code_start"), rs.getString("postal_code_end"), rs.getBigDecimal("fee"), rs.getInt("estimated_business_days"), rs.getBoolean("active")); }
    static final class ShippingZoneUnavailableException extends IllegalArgumentException { ShippingZoneUnavailableException() { super("Não há entrega disponível para o CEP informado."); } }
}
