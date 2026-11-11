package br.com.agendar.core.internal.migration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway do módulo {@code core}, com histórico e schema próprios.
 */
@Configuration
public class CoreFlywayConfig {

    public static final String SCHEMA = "core";
    public static final String HISTORY_TABLE = "flyway_schema_history_core";
    public static final String LOCATION = "classpath:db/migration/core";
    public static final String FLYWAY_BEAN_NAME = "coreFlyway";

    @Bean(name = FLYWAY_BEAN_NAME, initMethod = "migrate")
    public Flyway coreFlywayMigrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .table(HISTORY_TABLE)
                .locations(LOCATION)
                .load();
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor coreFlywayEntityManagerFactoryDependsOn() {
        return new EntityManagerFactoryDependsOnPostProcessor(FLYWAY_BEAN_NAME);
    }
}
