package br.com.agendar.scheduling.internal;

/**
 * Status do agendamento.
 *
 * <p>A máquina de estados (quais transições são válidas) fica para issue própria; aqui existe
 * só o enum, como a issue #6 delimita.
 */
public enum AppointmentStatus {
    PENDENTE,
    CONFIRMADO,
    EM_EXECUCAO,
    CONCLUIDO,
    CANCELADO,
    NO_SHOW
}
