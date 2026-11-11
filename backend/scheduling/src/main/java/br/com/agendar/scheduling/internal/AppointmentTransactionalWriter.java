package br.com.agendar.scheduling.internal;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.api.SchedulingFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma tentativa de criação de agendamento, em uma transação.
 *
 * <p>A proteção contra dois agendamentos no mesmo horário é a constraint do banco (#6). O código
 * não tenta "checar antes e depois salvar": entre a checagem e o insert cabe outro pedido.
 */
@Service
public class AppointmentTransactionalWriter {

    private final AppointmentRepository repositorio;
    private final Duration validadeChave;

    public AppointmentTransactionalWriter(AppointmentRepository repositorio,
                @Value("${agendar.scheduling.idempotencia.validade:PT24H}") Duration validadeChave) {
        this.repositorio = repositorio;
        this.validadeChave = validadeChave;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AppointmentView criar(SchedulingFacade.AgendarCommand comando) {
        Objects.requireNonNull(comando.tenantId(), "tenantId");
        Objects.requireNonNull(comando.idempotencyKey(), "idempotencyKey");

        // Mesma chave da mesma requisição devolve o agendamento já criado, sem inserir de novo.
        var existente = repositorio.findByTenantIdAndIdempotencyKey(
                comando.tenantId(), comando.idempotencyKey());
        if (existente.isPresent()) {
            Appointment a = existente.get();
            boolean mesmaRequisicao = Objects.equals(a.getPrestadorId(), comando.providerId())
                    && Objects.equals(a.getCustomerId(), comando.customerId())
                    && Objects.equals(a.getServiceId(), comando.serviceId())
                    && Objects.equals(a.getStartsAt(), comando.startsAt())
                    && Duration.between(a.getStartsAt(), a.getEndsAt())
                            .equals(Duration.ofMinutes(comando.durationMinutes()));
            if (!mesmaRequisicao) {
                throw new ChaveReutilizadaException(comando.idempotencyKey());
            }
            return paraView(a);
        }

        Instant inicio = comando.startsAt();
        Instant fim = inicio.plus(Duration.ofMinutes(comando.durationMinutes()));

        Appointment novo = new Appointment(UUID.randomUUID(), comando.tenantId(), comando.providerId(),
                inicio, fim, AppointmentStatus.PENDENTE);
        novo.vincularClienteEServico(comando.customerId(), comando.serviceId(), comando.tenantId());
        novo.registrarIdempotencia(comando.idempotencyKey(), Instant.now());

        try {
            repositorio.saveAndFlush(novo);
        } catch (org.springframework.dao.ConcurrencyFailureException e) {
            // deadlock / serialização: o agendamento pode já existir pela outra transação.
            throw new ConcorrenciaException(comando, e);
        } catch (DataIntegrityViolationException e) {
            // A constraint de exclusão é a autoridade sobre conflito de horário.
            // Ordem importa: se a chave de idempotência já existe, o pedido é uma repetição e
            // devolver o agendamento existente tem precedência sobre o conflito de horário — o
            // conflito é com o próprio agendamento que estamos devolvendo.
            // Depois de qualquer violação a transação está abortada: nem reler dá para fazer
            // aqui. Quem resolve é o retry, em transação nova, olhando a chave de idempotência.
            if (violaIndiceDeIdempotencia(e) || violaConstraintDeConflito(e)) {
                throw new ConcorrenciaException(comando, e);
            }
            throw e;
        }

        return paraView(novo);
    }

    /**
     * Conflito de concorrência do banco, a ser retentado.
     */
    public static class ConcorrenciaException extends RuntimeException {
        ConcorrenciaException(SchedulingFacade.AgendarCommand comando, Throwable causa) {
            super(causa);
        }
    }

    static boolean violaIndiceDeIdempotencia(Throwable e) {
        Throwable atual = e;
        while (atual != null) {
            String mensagem = atual.getMessage();
            if (mensagem != null && mensagem.contains("uq_appointment_tenant_idempotency")) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    static boolean violaConstraintDeConflito(Throwable e) {
        Throwable atual = e;
        while (atual != null) {
            String mensagem = atual.getMessage();
            if (mensagem != null && mensagem.contains("uq_appointment_prestador_during")) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    static AppointmentView paraView(Appointment a) {
        return new AppointmentView(a.getId(), a.getTenantId(), a.getPrestadorId(),
                a.getCustomerId(), a.getServiceId(), a.getStartsAt(), a.getEndsAt(),
                a.getStatus().name());
    }

    /**
     * Chave de idempotência usada com corpo diferente do original.
     */
    static class ChaveReutilizadaException extends RuntimeException {
        ChaveReutilizadaException(String chave) {
            super("Idempotency-Key já usada com outro conteúdo: " + chave);
        }
    }
}
