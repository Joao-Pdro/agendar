package br.com.agendar.scheduling.internal;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.api.SchedulingFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de horários livres.
 *
 * <p><b>Provisório:</b> o expediente vem de configuração global
 * ({@code agendar.scheduling.expediente.*}), igual para todos os prestadores, porque o cadastro
 * de disponibilidade do prestador (horário de trabalho, férias, bloqueios) ainda não existe —
 * é fase 2 e não tem issue. Quando existir, esta classe passa a receber a janela do prestador.
 */
@Service
public class AvailabilityService {

    private final AppointmentRepository repositorio;
    private final SlotCalculator calculadora;

    public AvailabilityService(AppointmentRepository repositorio,
                        @Value("${agendar.scheduling.expediente.inicio:08:00}") LocalTime inicio,
                        @Value("${agendar.scheduling.expediente.fim:18:00}") LocalTime fim,
                        @Value("${agendar.scheduling.slot.passo:PT30M}") Duration passo) {
        this.repositorio = repositorio;
        this.calculadora = new SlotCalculator(inicio, fim, passo);
    }

    @Transactional(readOnly = true)
    public List<SlotCalculator.Slot> slotsLivres(UUID prestadorId, LocalDate data, ZoneId fuso,
                                                 Duration duracao) {
        Instant inicioDoDia = data.atStartOfDay(fuso).toInstant();
        Instant fimDoDia = data.plusDays(1).atStartOfDay(fuso).toInstant();

        List<SlotCalculator.Ocupado> ocupados = repositorio
                .ocupadosNoPeriodo(prestadorId, inicioDoDia, fimDoDia).stream()
                .map(a -> new SlotCalculator.Ocupado(a.getStartsAt(), a.getEndsAt()))
                .toList();

        return calculadora.calcular(data, fuso, duracao, ocupados);
    }
}
