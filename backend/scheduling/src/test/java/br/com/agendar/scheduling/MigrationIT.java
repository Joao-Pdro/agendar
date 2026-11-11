package br.com.agendar.scheduling;

import br.com.agendar.scheduling.internal.migration.SchedulingFlywayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confere que a migration cria exatamente o que a #6 pede: btree_gist, tipagens e a constraint
 * de exclusão com o filtro de CANCELADO.
 */
@Testcontainers
class MigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationCriaAConstraintDeExclusaoComTiposCorretos() {
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SchedulingFlywayConfig.SCHEMA)
                .defaultSchema(SchedulingFlywayConfig.SCHEMA)
                .table(SchedulingFlywayConfig.HISTORY_TABLE)
                .locations(SchedulingFlywayConfig.LOCATION)
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        Integer btreeGist = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(btreeGist).isEqualTo(1);

        List<Map<String, Object>> colunas = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'scheduling' AND table_name = 'appointment'
                ORDER BY ordinal_position
                """);

        assertThat(colunas).extracting(c -> c.get("column_name")).containsExactly(
                "id", "tenant_id", "prestador_id", "starts_at", "ends_at",
                "status", "version", "created_at", "updated_at", "during");

        for (String esperada : List.of("starts_at", "ends_at", "created_at", "updated_at")) {
            Map<String, Object> col = colunas.stream()
                    .filter(c -> esperada.equals(c.get("column_name")))
                    .findFirst().orElseThrow();
            assertThat(col.get("data_type")).as(esperada).isEqualTo("timestamp with time zone");
        }

        Map<String, Object> during = colunas.stream()
                .filter(c -> "during".equals(c.get("column_name")))
                .findFirst().orElseThrow();
        assertThat(during.get("data_type")).isEqualTo("tstzrange");

        Map<String, Object> def = jdbc.queryForMap("""
                SELECT pg_get_constraintdef(oid) AS def
                FROM pg_constraint
                WHERE conname = 'uq_appointment_prestador_during'
                """);
        String definicao = String.valueOf(def.get("def"));
        assertThat(definicao).contains("EXCLUDE USING gist");
        assertThat(definicao).contains("prestador_id WITH =");
        assertThat(definicao).contains("during WITH &&");
        assertThat(definicao).contains("CANCELADO");
    }
}
