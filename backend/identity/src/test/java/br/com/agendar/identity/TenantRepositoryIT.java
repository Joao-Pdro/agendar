package br.com.agendar.identity;

import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(classes = TenantRepositoryIT.TestConfig.class)
class TenantRepositoryIT {

    /**
     * O módulo `identity` não é uma aplicação Spring Boot própria: o contexto de teste
     * monta só o que este módulo precisa (JPA + Flyway + repositórios do módulo).
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Tenant.class)
    @EnableJpaRepositories(basePackageClasses = TenantRepository.class)
    static class TestConfig {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TenantRepository repository;

    @Test
    void tenantCriadoSemInformarAFlagPersisteFalse() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "Empresa Um", "empresa-um");

        repository.saveAndFlush(tenant);

        Tenant recarregado = repository.findBySlug("empresa-um").orElseThrow();
        assertThat(recarregado.isCustomerSelfBooking()).isFalse();
        assertThat(recarregado.getCreatedAt()).isNotNull();
        assertThat(recarregado.getUpdatedAt()).isNotNull();
    }

    @Test
    void alterarAFlagParaTruePersiste() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "Empresa Dois", "empresa-dois");
        repository.saveAndFlush(tenant);

        tenant.setCustomerSelfBooking(true);
        repository.saveAndFlush(tenant);

        Tenant recarregado = repository.findBySlug("empresa-dois").orElseThrow();
        assertThat(recarregado.isCustomerSelfBooking()).isTrue();
    }
}
