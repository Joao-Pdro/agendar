package br.com.agendar.identity;

import br.com.agendar.identity.api.IdentityFacade;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A flag exposta pela API do módulo reflete o que está no banco, e nasce desligada.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(classes = IdentityFacadeIT.TestConfig.class)
class IdentityFacadeIT {

    @Configuration
    @EnableAutoConfiguration
    // Só o pacote do módulo, sem a camada de segurança (que exige configuração de JWT).
    @ComponentScan(basePackages = "br.com.agendar.identity.internal",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "br\\.com\\.agendar\\.identity\\.internal\\.(security|web)\\..*"))
    @EntityScan(basePackageClasses = Tenant.class)
    @EnableJpaRepositories(basePackageClasses = TenantRepository.class)
    static class TestConfig {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private IdentityFacade facade;

    @Autowired
    private TenantRepository repository;

    @Test
    void flagNasceDesligadaELigaAoSerAlterada() {
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(new Tenant(id, "Empresa Três", "empresa-tres"));

        assertThat(facade.isCustomerSelfBookingEnabled(id)).isFalse();

        Tenant tenant = repository.findById(id).orElseThrow();
        tenant.setCustomerSelfBooking(true);
        repository.saveAndFlush(tenant);

        assertThat(facade.isCustomerSelfBookingEnabled(id)).isTrue();
        assertThat(facade.findTenant(id)).get()
                .extracting("slug", "customerSelfBooking")
                .containsExactly("empresa-tres", true);
    }

    @Test
    void tenantInexistenteNaoHabilitaOBloqueio() {
        assertThat(facade.isCustomerSelfBookingEnabled(UUID.randomUUID())).isFalse();
        assertThat(facade.findTenant(UUID.randomUUID())).isEmpty();
    }
}
