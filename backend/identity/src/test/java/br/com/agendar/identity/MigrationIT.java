package br.com.agendar.identity;

import br.com.agendar.identity.internal.migration.IdentityFlywayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a migration do módulo cria o schema e a tabela exatamente como a issue pede,
 * e que o histórico fica na tabela própria do módulo.
 */
@Testcontainers
class MigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationCriaASchemaIdentityComATabelaTenant() {
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(IdentityFlywayConfig.SCHEMA)
                .defaultSchema(IdentityFlywayConfig.SCHEMA)
                .table(IdentityFlywayConfig.HISTORY_TABLE)
                .locations(IdentityFlywayConfig.LOCATION)
                .load();

        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        List<Map<String, Object>> colunas = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = 'identity' AND table_name = 'tenant'
                ORDER BY ordinal_position
                """);

        assertThat(colunas).extracting(c -> c.get("column_name"))
                .containsExactly("id", "name", "slug", "customer_self_booking", "created_at", "updated_at");

        Map<String, Object> flag = colunas.stream()
                .filter(c -> "customer_self_booking".equals(c.get("column_name")))
                .findFirst().orElseThrow();
        assertThat(flag.get("data_type")).isEqualTo("boolean");
        assertThat(flag.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(flag.get("column_default"))).isEqualTo("false");

        Map<String, Object> createdAt = colunas.stream()
                .filter(c -> "created_at".equals(c.get("column_name")))
                .findFirst().orElseThrow();
        assertThat(createdAt.get("data_type")).isEqualTo("timestamp with time zone");

        Integer fks = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_type = 'FOREIGN KEY' AND table_schema = 'identity'
                """, Integer.class);
        assertThat(fks).isZero();

        List<Map<String, Object>> historico = jdbc.queryForList(
                "SELECT version, success FROM identity.flyway_schema_history_identity WHERE version IS NOT NULL");
        assertThat(historico).hasSize(1);
        assertThat(historico.getFirst().get("version")).isEqualTo("1");
        assertThat(historico.getFirst().get("success")).isEqualTo(true);
    }
}
