package br.com.agendar.scheduling.internal.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Expediente padrão de um tenant, em um dia da semana.
 *
 * <p>Os horários são hora local, sem fuso: o fuso vem de {@link TenantTimezone}. Isso mantém a
 * grade estável quando o horário de verão muda.
 */
@Entity
@Table(name = "tenant_availability", schema = "scheduling")
@IdClass(TenantAvailability.Key.class)
public class TenantAvailability {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "weekday", nullable = false)
    private short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected TenantAvailability() {
        // exigido pelo JPA
    }

    public TenantAvailability(UUID tenantId, DayOfWeek dia, LocalTime startTime, LocalTime endTime) {
        validarPeriodo(startTime, endTime);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.weekday = (short) dia.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public DayOfWeek getWeekday() {
        return DayOfWeek.of(weekday);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    static void validarPeriodo(LocalTime inicio, LocalTime fim) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("start_time precisa ser antes de end_time");
        }
    }

    /**
     * Chave composta (tenant_id, weekday).
     */
    public static class Key implements Serializable {
        private UUID tenantId;
        private short weekday;

        public Key() {
        }

        public Key(UUID tenantId, short weekday) {
            this.tenantId = tenantId;
            this.weekday = weekday;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return weekday == other.weekday && Objects.equals(tenantId, other.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, weekday);
        }
    }
}
