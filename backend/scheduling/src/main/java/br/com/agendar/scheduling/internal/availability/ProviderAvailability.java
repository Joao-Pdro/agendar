package br.com.agendar.scheduling.internal.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Expediente próprio de um prestador, em um dia da semana.
 *
 * <p>Se o prestador tem qualquer linha aqui, ela substitui integralmente o expediente do tenant —
 * não é um complemento. Um dia sem linha significa dia sem atendimento para aquele prestador.
 */
@Entity
@Table(name = "provider_availability", schema = "scheduling")
public class ProviderAvailability {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private UUID providerId;

    @Column(name = "weekday", nullable = false)
    private short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected ProviderAvailability() {
        // exigido pelo JPA
    }

    public ProviderAvailability(UUID id, UUID tenantId, UUID providerId, DayOfWeek dia,
                                LocalTime startTime, LocalTime endTime) {
        TenantAvailability.validarPeriodo(startTime, endTime);
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.weekday = (short) dia.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProviderId() {
        return providerId;
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
}
