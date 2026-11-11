package br.com.agendar.scheduling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agendamento de um serviço para um prestador, dentro de um tenant.
 *
 * <p>A sobreposição de horários do mesmo prestador é impedida no banco (constraint de exclusão
 * sobre {@code during}); o {@code version} é o lock otimista para atualizações concorrentes da
 * mesma linha.
 *
 * <p>A coluna {@code during} é gerada pelo banco a partir de {@code starts_at}/{@code ends_at} e
 * por isso não é mapeada: o JPA trabalha com os dois {@code timestamptz}.
 */
@Entity
@Table(name = "appointment", schema = "scheduling")
public class Appointment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "prestador_id", nullable = false, updatable = false)
    private UUID prestadorId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "idempotency_created_at")
    private Instant idempotencyCreatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Appointment() {
        // exigido pelo JPA
    }

    public Appointment(UUID id, UUID tenantId, UUID prestadorId, Instant startsAt, Instant endsAt,
                       AppointmentStatus status) {
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt precisa ser anterior a endsAt");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.prestadorId = Objects.requireNonNull(prestadorId, "prestadorId");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPrestadorId() {
        return prestadorId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Vincula cliente e serviço ao agendamento.
     */
    public void vincularClienteEServico(UUID customerId, UUID serviceId, UUID tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new IllegalArgumentException("tenant não confere com o agendamento");
        }
        this.customerId = customerId;
        this.serviceId = serviceId;
    }

    public void registrarIdempotencia(String chave, Instant quando) {
        this.idempotencyKey = chave;
        this.idempotencyCreatedAt = quando;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getIdempotencyCreatedAt() {
        return idempotencyCreatedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Appointment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
