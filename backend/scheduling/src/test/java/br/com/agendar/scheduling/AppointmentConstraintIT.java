package br.com.agendar.scheduling;

import br.com.agendar.scheduling.internal.Appointment;
import br.com.agendar.scheduling.internal.AppointmentRepository;
import br.com.agendar.scheduling.internal.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que a proteção contra dois agendamentos sobrepostos do mesmo prestador está no banco,
 * não no código, e que o lock otimista funciona.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(classes = AppointmentConstraintIT.TestConfig.class)
class AppointmentConstraintIT {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Appointment.class)
    @EnableJpaRepositories(basePackageClasses = AppointmentRepository.class)
    static class TestConfig {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AppointmentRepository repository;

    @Autowired
    private EntityManager entityManager;

    private static final Instant BASE = Instant.parse("2026-03-10T13:00:00Z");

    @Test
    void doisAgendamentosSobrepostosDoMesmoPrestadorFalham() {
        UUID prestador = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        Appointment primeiro = new Appointment(UUID.randomUUID(), tenant, prestador,
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.PENDENTE);
        repository.saveAndFlush(primeiro);

        Appointment sobreposto = new Appointment(UUID.randomUUID(), tenant, prestador,
                BASE.plus(30, ChronoUnit.MINUTES), BASE.plus(90, ChronoUnit.MINUTES),
                AppointmentStatus.PENDENTE);

        assertThatThrownBy(() -> repository.saveAndFlush(sobreposto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_appointment_prestador_during");
    }

    @Test
    void agendamentoCanceladoNaoBloqueiaNovoHorarioNoMesmoIntervalo() {
        UUID prestador = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        Appointment cancelado = new Appointment(UUID.randomUUID(), tenant, prestador,
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.CANCELADO);
        repository.saveAndFlush(cancelado);

        Appointment novo = new Appointment(UUID.randomUUID(), tenant, prestador,
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.PENDENTE);

        repository.saveAndFlush(novo);

        assertThat(repository.findById(novo.getId())).isPresent();
    }

    @Test
    void prestadorDiferentePodeTerOmesmoHorario() {
        UUID tenant = UUID.randomUUID();

        repository.saveAndFlush(new Appointment(UUID.randomUUID(), tenant, UUID.randomUUID(),
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.PENDENTE));
        repository.saveAndFlush(new Appointment(UUID.randomUUID(), tenant, UUID.randomUUID(),
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.PENDENTE));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasAtualizacoesConcorrentesDaMesmaLinhaFalhamComOptimisticLock() {
        UUID id = UUID.randomUUID();
        Appointment original = new Appointment(id, UUID.randomUUID(), UUID.randomUUID(),
                BASE, BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.PENDENTE);
        repository.saveAndFlush(original);

        // Duas cópias da mesma linha, carregadas separadamente: simulam duas transações.
        Appointment copiaA = repository.findById(id).orElseThrow();
        Appointment copiaB = repository.findById(id).orElseThrow();

        copiaA.setStatus(AppointmentStatus.CONFIRMADO);
        repository.saveAndFlush(copiaA);

        copiaB.setStatus(AppointmentStatus.NO_SHOW);

        // O Hibernate lança ObjectOptimisticLockingFailureException, que é um
        // OptimisticLockException do Spring (e tem a OptimisticLockException do JPA como causa).
        assertThatThrownBy(() -> repository.saveAndFlush(copiaB))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class)
                .hasMessageContaining("Row was updated or deleted by another transaction");
    }
}
