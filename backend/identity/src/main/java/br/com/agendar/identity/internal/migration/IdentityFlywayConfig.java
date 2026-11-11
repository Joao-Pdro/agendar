package br.com.agendar.identity.internal.migration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway do módulo {@code identity}.
 *
 * <p>Cada módulo tem o seu próprio histórico de migrations, porque todos numeram a partir de
 * {@code V1__}. Este Flyway cria o schema {@code identity} e aplica só
 * {@code classpath:db/migration/identity}, na tabela {@code flyway_schema_history_identity}.
 *
 * <p>A migration precisa terminar antes de o Hibernate validar o schema, já que a aplicação roda
 * com {@code ddl-auto: validate}. O {@link EntityManagerFactoryDependsOnPostProcessor} faz o
 * {@code entityManagerFactory} depender deste bean, sem que o módulo precise conhecer o
 * {@code platform} nem o {@code platform} precise conhecer este bean.
 */
@Configuration
public class IdentityFlywayConfig {

    public static final String SCHEMA = "identity";
    public static final String HISTORY_TABLE = "flyway_schema_history_identity";
    public static final String LOCATION = "classpath:db/migration/identity";
    public static final String FLYWAY_BEAN_NAME = "identityFlyway";

    @Bean(name = FLYWAY_BEAN_NAME, initMethod = "migrate")
    public Flyway identityFlywayMigrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .table(HISTORY_TABLE)
                .locations(LOCATION)
                .load();
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor identityFlywayEntityManagerFactoryDependsOn() {
        return new EntityManagerFactoryDependsOnPostProcessor(FLYWAY_BEAN_NAME);
    }
}
