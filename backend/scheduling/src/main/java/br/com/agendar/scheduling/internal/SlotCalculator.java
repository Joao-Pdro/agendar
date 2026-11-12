package br.com.agendar.scheduling.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula os horários livres de um prestador em um dia.
 *
 * <p>A janela de trabalho vem do expediente do prestador ou do padrão do tenant (issue #39), em
 * **hora local** do tenant. A conversão para instantes absolutos acontece aqui, a cada dia: é o
 * que mantém a grade estável quando o horário de verão muda, porque a hora local continua a
 * mesma e o offset se ajusta sozinho.
 *
 * <p>Regra do slot: o intervalo {@code [inicio, inicio + duracao)} precisa caber dentro da janela
 * e não sobrepor nenhum agendamento ativo (status diferente de {@code CANCELADO}). Os slots são
 * oferecidos em passos de {@code passo}, começando no início da janela.
 */
public final class SlotCalculator {

    private final Duration passo;

    public SlotCalculator(Duration passo) {
        if (passo.isNegative() || passo.isZero()) {
            throw new IllegalArgumentException("passo precisa ser positivo");
        }
        this.passo = passo;
    }

    /**
     * @param dia        data consultada, no fuso do tenant
     * @param fuso       fuso do tenant
     * @param inicio     hora local de início do expediente
     * @param fim        hora local de fim do expediente
     * @param duracao    duração do serviço
     * @param ocupados   agendamentos ativos do prestador naquele dia
     */
    public List<Slot> calcular(java.time.LocalDate dia, ZoneId fuso, LocalTime inicio, LocalTime fim,
                               Duration duracao, List<Ocupado> ocupados) {
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("início do expediente precisa ser antes do fim");
        }

        Instant inicioJanela = emInstante(dia, inicio, fuso);
        Instant fimJanela = emInstante(dia, fim, fuso);

        List<Ocupado> ordenados = ocupados.stream()
                .sorted(Comparator.comparing(Ocupado::inicio))
                .toList();

        List<Slot> livres = new ArrayList<>();
        for (Instant candidato = inicioJanela; !candidato.plus(duracao).isAfter(fimJanela);
             candidato = candidato.plus(passo)) {
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
     * Converte hora local do tenant em instante absoluto.
     *
     * <p>Usa a regra do fuso para aquela data, então um dia com horário de verão produz o offset
     * correto sem nenhum cálculo manual.
     */
    private static Instant emInstante(java.time.LocalDate dia, LocalTime hora, ZoneId fuso) {
        return ZonedDateTime.of(dia, hora, fuso).toInstant();
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
