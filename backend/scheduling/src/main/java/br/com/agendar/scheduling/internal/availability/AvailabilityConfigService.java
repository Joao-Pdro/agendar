package br.com.agendar.scheduling.internal.availability;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consulta e configuração do expediente semanal.
 *
 * <p>Regra do MVP: se o prestador tem linhas próprias, elas valem integralmente; senão vale o
 * padrão do tenant. Dia sem linha é dia sem atendimento.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityConfigService {

    private final TenantTimezoneRepository fusos;
    private final TenantAvailabilityRepository padraoDoTenant;
    private final ProviderAvailabilityRepository doPrestador;

    public AvailabilityConfigService(TenantTimezoneRepository fusos,
                                     TenantAvailabilityRepository padraoDoTenant,
                                     ProviderAvailabilityRepository doPrestador) {
        this.fusos = fusos;
        this.padraoDoTenant = padraoDoTenant;
        this.doPrestador = doPrestador;
    }

    /**
     * Fuso do tenant; {@code America/Sao_Paulo} quando ainda não configurado.
     */
    public ZoneId fusoDoTenant(UUID tenantId) {
        return fusos.findById(tenantId)
                .map(TenantTimezone::zona)
                .orElseGet(() -> ZoneId.of(TenantTimezone.PADRAO));
    }

    /**
     * Janela de trabalho do prestador em um dia: a dele, se tiver, senão a do tenant.
     */
    public Optional<Janela> janelaDoDia(UUID tenantId, UUID providerId, DayOfWeek dia) {
        var doProprioPrestador = doPrestador.findAllByProviderIdAndTenantId(providerId, tenantId);

        // A regra é sobre o prestador ter expediente próprio, não sobre ter linha naquele dia:
        // quem configurou o próprio expediente não herda o padrão do tenant em dia vago.
        if (!doProprioPrestador.isEmpty()) {
            return doProprioPrestador.stream()
                    .filter(a -> a.getWeekday() == dia)
                    .findFirst()
                    .map(a -> new Janela(dia, a.getStartTime(), a.getEndTime(), true));
        }

        return padraoDoTenant.findAllByTenantId(tenantId).stream()
                .filter(a -> a.getWeekday() == dia)
                .findFirst()
                .map(a -> new Janela(dia, a.getStartTime(), a.getEndTime(), false));
    }

    /**
     * Janela de trabalho no dia da data informada.
     */
    public Optional<Janela> janelaDoDia(UUID tenantId, UUID providerId, LocalDate data) {
        return janelaDoDia(tenantId, providerId, data.getDayOfWeek());
    }

    public Map<DayOfWeek, Janela> expedienteDoTenant(UUID tenantId) {
        return padraoDoTenant.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantAvailability::getWeekday,
                        a -> new Janela(a.getWeekday(), a.getStartTime(), a.getEndTime(), false)));
    }

    public Map<DayOfWeek, Janela> expedienteDoPrestador(UUID tenantId, UUID providerId) {
        return doPrestador.findAllByProviderIdAndTenantId(providerId, tenantId).stream()
                .collect(Collectors.toMap(ProviderAvailability::getWeekday,
                        a -> new Janela(a.getWeekday(), a.getStartTime(), a.getEndTime(), true)));
    }

    @Transactional
    public void definirExpedienteDoTenant(UUID tenantId, List<Janela> janelas) {
        padraoDoTenant.deleteAllByTenantId(tenantId);
        padraoDoTenant.flush();
        padraoDoTenant.saveAll(janelas.stream()
                .map(j -> new TenantAvailability(tenantId, j.dia(), j.inicio(), j.fim()))
                .toList());
    }

    @Transactional
    public void definirExpedienteDoPrestador(UUID tenantId, UUID providerId, List<Janela> janelas) {
        doPrestador.deleteAllByProviderIdAndTenantId(providerId, tenantId);
        doPrestador.flush();
        doPrestador.saveAll(janelas.stream()
                .map(j -> new ProviderAvailability(UUID.randomUUID(), tenantId, providerId,
                        j.dia(), j.inicio(), j.fim()))
                .toList());
    }

    @Transactional
    public void definirFuso(UUID tenantId, String timezone) {
        TenantTimezone fuso = fusos.findById(tenantId)
                .orElseGet(() -> new TenantTimezone(tenantId, TenantTimezone.PADRAO));
        fuso.alterar(timezone);
        fusos.saveAndFlush(fuso);
    }

    /**
     * Janela de trabalho de um dia, em hora local do tenant.
     *
     * @param propria {@code true} quando veio do expediente do próprio prestador
     */
    public record Janela(DayOfWeek dia, LocalTime inicio, LocalTime fim, boolean propria) {

        public Janela(DayOfWeek dia, LocalTime inicio, LocalTime fim) {
            this(dia, inicio, fim, false);
        }
    }

    /**
     * Converte uma lista de janelas em mapa por dia.
     */
    public static Map<DayOfWeek, Janela> porDia(List<Janela> janelas) {
        return janelas.stream().collect(Collectors.toMap(Janela::dia, Function.identity()));
    }
}
