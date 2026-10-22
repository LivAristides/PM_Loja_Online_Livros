package br.com.livrariaonline.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheVersionedInfrastructureMigration() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class);
        Integer crossCuttingMigrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
                Integer.class);
        Integer identityMigrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = true",
                Integer.class);
        String demoDataPolicy = jdbcTemplate.queryForObject(
                "SELECT metadata_value FROM application_metadata WHERE metadata_key = 'demo_data_policy'",
                String.class);
        String outboxTable = jdbcTemplate.queryForObject("SELECT to_regclass('outbox_message')", String.class);
        String auditTable = jdbcTemplate.queryForObject("SELECT to_regclass('audit_log')", String.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(crossCuttingMigrationCount).isEqualTo(1);
        assertThat(identityMigrationCount).isEqualTo(1);
        assertThat(demoDataPolicy).isEqualTo("no-personal-data");
        assertThat(outboxTable).isEqualTo("outbox_message");
        assertThat(auditTable).isEqualTo("audit_log");
    }
}
