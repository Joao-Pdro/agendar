package br.com.agendar.scheduling;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.api.SchedulingFacade;
import br.com.agendar.scheduling.internal.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corrida de idempotência: duas requisições com a mesma chave, ao mesmo tempo, contra o banco.
 *
 * <p>A chave promete "mesmo resultado", então as duas precisam terminar com **o mesmo
 * agendamento** — nem erro, nem dois registros.
 */
@SpringBootTest(classes = IdempotencyRaceIT.TestConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(classes = IdempotencyRaceIT.TestConfig.class)
@org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class IdempotencyRaceIT {

    @Configuration
    @EnableAutoConfiguration
    // O serviço de agendamento não depende do catálogo; aqui basta o pacote do módulo.
    @ComponentScan(basePackageClasses = AppointmentRepository.class,
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "br\\.com\\.agendar\\.scheduling\\.internal\\.(migration|web|dependencias)\\..*"))
    @EntityScan(basePackageClasses = AppointmentRepository.class)
    @EnableJpaRepositories(basePackageClasses = AppointmentRepository.class)
    static class TestConfig {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SchedulingFacade facade;

    @Autowired
    private AppointmentRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    void duasRequisicoesConcorrentesComAMesmaChaveDevolvemOMesmoAgendamento() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID prestadorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant inicio = Instant.parse("2031-05-05T10:00:00Z");
        String chave = "corrida-" + UUID.randomUUID();

        var comando = new SchedulingFacade.AgendarCommand(tenantId, prestadorId, customerId,
                serviceId, inicio, 60, chave);

        int concorrentes = 2;
        CyclicBarrier largada = new CyclicBarrier(concorrentes);
        ExecutorService pool = Executors.newFixedThreadPool(concorrentes);
        List<Future<AppointmentView>> futuros = new ArrayList<>();

        try {
            for (int i = 0; i < concorrentes; i++) {
                Callable<AppointmentView> tarefa = () -> {
                    largada.await();
                    return facade.agendar(comando);
                };
                futuros.add(pool.submit(tarefa));
            }

            List<UUID> ids = new ArrayList<>();
            for (Future<AppointmentView> futuro : futuros) {
                ids.add(futuro.get().id());
            }

            assertThat(ids).as("todas as requisições devolvem o mesmo agendamento")
                    .containsOnly(ids.getFirst());
            assertThat(repositorio.count()).as("um único agendamento no banco").isEqualTo(1);
        } finally {
            // Sem shutdownNow: interromper threads no meio da conexão faz o driver estourar
            // EOFException e mascara o resultado do teste.
            pool.shutdown();
            assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
}
