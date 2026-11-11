package br.com.agendar.scheduling.internal;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.api.SchedulingFacade;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Criação de agendamento com idempotência e retry em conflito de concorrência.
 *
 * <p>A criação em si vive em {@link AppointmentTransactionalWriter}, que é o bean com
 * {@code @Transactional(REQUIRES_NEW)}. Aqui só ficam as tentativas: chamar o método
 * transacional a partir desta mesma classe não abriria transação nova (é o proxy do Spring que
 * abre), e cada tentativa precisa de uma transação própria porque a anterior aborta.
 *
 * <p>Depois de uma violação de constraint o PostgreSQL deixa a transação abortada, então nem a
 * releitura do agendamento existente pode acontecer lá dentro. A cada nova tentativa a busca pela
 * chave de idempotência roda em transação limpa e já enxerga o commit da concorrente.
 */
@Service
public class AppointmentService {

    private static final int MAX_TENTATIVAS = 3;

    private final AppointmentTransactionalWriter writer;
    private final AppointmentRepository repositorio;

    public AppointmentService(AppointmentTransactionalWriter writer, AppointmentRepository repositorio) {
        this.writer = writer;
        this.repositorio = repositorio;
    }

    public AppointmentView agendar(SchedulingFacade.AgendarCommand comando) {
        Objects.requireNonNull(comando.tenantId(), "tenantId");
        Objects.requireNonNull(comando.idempotencyKey(), "idempotencyKey");

        AppointmentTransactionalWriter.ConcorrenciaException ultima = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                return writer.criar(comando);
            } catch (AppointmentTransactionalWriter.ConcorrenciaException e) {
                ultima = e;
            }
        }

        // Esgotou as tentativas: se a chave já tem agendamento, ele é a resposta correta.
        // Se não, o conflito é de horário mesmo e o cliente merece 409.
        var existente = repositorio.findByTenantIdAndIdempotencyKey(
                comando.tenantId(), comando.idempotencyKey());
        if (existente.isPresent()) {
            return AppointmentTransactionalWriter.paraView(existente.get());
        }
        throw new SchedulingFacade.SlotConflictException(
                "O prestador já tem agendamento nesse intervalo.");
    }
}
