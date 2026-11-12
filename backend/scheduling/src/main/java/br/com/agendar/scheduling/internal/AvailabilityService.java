package br.com.agendar.scheduling.internal;

import br.com.agendar.scheduling.api.AppointmentView;
import br.com.agendar.scheduling.internal.availability.AvailabilityConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de horários livres.
 *
 * <p>A janela de trabalho vem do expediente do prestador ou do padrão do tenant (#39); não existe
 * mais o expediente global provisório da #18. Dia sem expediente devolve lista vazia.
 */
@Service
public class AvailabilityService {

    private final AppointmentRepository repositorio;
    private final AvailabilityConfigService expediente;
    private final SlotCalculator calculadora;

    public AvailabilityService(AppointmentRepository repositorio,
                               AvailabilityConfigService expediente,
                               @Value("${agendar.scheduling.slot.passo:PT30M}") Duration passo) {
        this.repositorio = repositorio;
        this.expediente = expediente;
        this.calculadora = new SlotCalculator(passo);
    }

    /**
     * Agendamentos do prestador no intervalo, sem os cancelados, em ordem crescente.
     */
    @Transactional(readOnly = true)
    public List<AppointmentView> agendamentos(UUID tenantId, UUID prestadorId, Instant inicio, Instant fim) {
        return repositorio.doPrestadorNoPeriodo(tenantId, prestadorId, inicio, fim).stream()
                .map(a -> new AppointmentView(a.getId(), a.getTenantId(), a.getPrestadorId(),
                        a.getCustomerId(), a.getServiceId(), a.getStartsAt(), a.getEndsAt(),
                        a.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotCalculator.Slot> slotsLivres(UUID tenantId, UUID prestadorId, LocalDate data,
                                                 ZoneId fuso, Duration duracao) {
        var janela = expediente.janelaDoDia(tenantId, prestadorId, data);
        if (janela.isEmpty()) {
            return List.of();
        }

        Instant inicioDoDia = data.atStartOfDay(fuso).toInstant();
        Instant fimDoDia = data.plusDays(1).atStartOfDay(fuso).toInstant();

        List<SlotCalculator.Ocupado> ocupados = repositorio
                .ocupadosNoPeriodo(prestadorId, inicioDoDia, fimDoDia).stream()
                .map(a -> new SlotCalculator.Ocupado(a.getStartsAt(), a.getEndsAt()))
                .toList();

        return calculadora.calcular(data, fuso, janela.get().inicio(), janela.get().fim(),
                duracao, ocupados);
    }

    /**
     * Indica se o instante está dentro do expediente do prestador naquele dia.
     *
     * <p>Usado pelo {@code POST /appointments} para recusar horário fora do expediente.
     */
    @Transactional(readOnly = true)
    public boolean dentroDoExpediente(UUID tenantId, UUID prestadorId, Instant inicio, Instant fim) {
        ZoneId fuso = expediente.fusoDoTenant(tenantId);
        LocalDate dia = inicio.atZone(fuso).toLocalDate();
        var janela = expediente.janelaDoDia(tenantId, prestadorId, dia);
        if (janela.isEmpty()) {
            return false;
        }
        Instant inicioJanela = dia.atTime(janela.get().inicio()).atZone(fuso).toInstant();
        Instant fimJanela = dia.atTime(janela.get().fim()).atZone(fuso).toInstant();
        return !inicio.isBefore(inicioJanela) && !fim.isAfter(fimJanela);
    }
}
