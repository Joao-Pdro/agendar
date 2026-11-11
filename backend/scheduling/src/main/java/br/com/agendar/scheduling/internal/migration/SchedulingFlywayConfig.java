package br.com.agendar.scheduling.internal.migration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway do módulo {@code scheduling}.
 *
 * <p>Histórico próprio ({@code flyway_schema_history_scheduling}), porque todos os módulos
 * numeram as migrations a partir de {@code V1__}. A migration roda antes de o Hibernate validar
 * o schema ({@code ddl-auto: validate}).
 */
@Configuration
public class SchedulingFlywayConfig {

    public static final String SCHEMA = "scheduling";
    public static final String HISTORY_TABLE = "flyway_schema_history_scheduling";
    public static final String LOCATION = "classpath:db/migration/scheduling";
    public static final String FLYWAY_BEAN_NAME = "schedulingFlyway";

    @Bean(name = FLYWAY_BEAN_NAME, initMethod = "migrate")
    public Flyway schedulingFlywayMigrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .table(HISTORY_TABLE)
                .locations(LOCATION)
                .load();
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor schedulingFlywayEntityManagerFactoryDependsOn() {
        return new EntityManagerFactoryDependsOnPostProcessor(FLYWAY_BEAN_NAME);
    }
}
