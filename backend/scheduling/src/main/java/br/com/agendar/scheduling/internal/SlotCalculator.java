package br.com.agendar.scheduling.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula os horários livres de um prestador em um dia.
 *
 * <p>O cadastro de disponibilidade do prestador (horário de trabalho, férias, bloqueios) ainda
 * não existe — é fase 2 e não tem issue. Enquanto isso, a janela de trabalho é configurável e
 * igual para todos os prestadores do tenant:
 * {@code agendar.scheduling.expediente.inicio} e {@code ...fim}.
 *
 * <p>Regra do slot: o intervalo {@code [inicio, inicio + duracao)} precisa caber dentro do
 * expediente e não sobrepor nenhum agendamento ativo (status diferente de {@code CANCELADO}).
 * Os slots são oferecidos em passos de {@code passo-minutos}, começando no início do expediente.
 */
public final class SlotCalculator {

    private final LocalTime inicioExpediente;
    private final LocalTime fimExpediente;
    private final Duration passo;

    public SlotCalculator(LocalTime inicioExpediente, LocalTime fimExpediente, Duration passo) {
        if (!inicioExpediente.isBefore(fimExpediente)) {
            throw new IllegalArgumentException("início do expediente precisa ser antes do fim");
        }
        if (passo.isNegative() || passo.isZero()) {
            throw new IllegalArgumentException("passo precisa ser positivo");
        }
        this.inicioExpediente = inicioExpediente;
        this.fimExpediente = fimExpediente;
        this.passo = passo;
    }

    /**
     * @param data        dia consultado, no fuso informado
     * @param fuso        fuso do tenant
     * @param duracao     duração do serviço
     * @param ocupados    agendamentos ativos do prestador naquele dia
     */
    public List<Slot> calcular(LocalDate data, ZoneId fuso, Duration duracao, List<Ocupado> ocupados) {
        Instant inicio = data.atTime(inicioExpediente).atZone(fuso).toInstant();
        Instant fim = data.atTime(fimExpediente).atZone(fuso).toInstant();

        List<Ocupado> ordenados = ocupados.stream()
                .sorted(Comparator.comparing(Ocupado::inicio))
                .toList();

        List<Slot> livres = new ArrayList<>();
        for (Instant candidato = inicio; !candidato.plus(duracao).isAfter(fim); candidato = candidato.plus(passo)) {
            Instant inicioSlot = candidato;
            Instant candidatoFim = candidato.plus(duracao);
            boolean conflita = ordenados.stream().anyMatch(o ->
                    inicioSlot.isBefore(o.fim()) && candidatoFim.isAfter(o.inicio()));
            if (!conflita) {
                livres.add(new Slot(candidato, candidatoFim));
            }
        }
        return livres;
    }

    /**
     * Intervalo já ocupado por um agendamento ativo.
     */
    public record Ocupado(Instant inicio, Instant fim) {
    }

    /**
     * Slot livre. Os instantes são absolutos (UTC); quem exibe converte para o fuso do tenant.
     */
    public record Slot(Instant startsAt, Instant endsAt) {
    }
}
