package br.com.agendar.core.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Serviço do catálogo de um tenant.
 *
 * <p>Soft delete ({@code deleted_at}): o serviço sai da lista, mas continua existindo para os
 * agendamentos antigos que apontam para ele.
 */
@Entity
@Table(name = "service", schema = "core")
public class Service {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Service() {
        // exigido pelo JPA
    }

    public Service(UUID id, UUID tenantId, String name, int durationMinutes, BigDecimal basePrice) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes precisa ser maior que zero");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice não pode ser negativo");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = requireTexto(name, "name");
        this.durationMinutes = durationMinutes;
        this.basePrice = basePrice;
    }

    public void renomear(String name) {
        this.name = requireTexto(name, "name");
    }

    public void alterarDuracao(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes precisa ser maior que zero");
        }
        this.durationMinutes = durationMinutes;
    }

    public void alterarPreco(BigDecimal basePrice) {
        if (basePrice == null || basePrice.signum() < 0) {
            throw new IllegalArgumentException("basePrice não pode ser negativo");
        }
        this.basePrice = basePrice;
    }

    /**
     * Soft delete. Idempotente: chamar de novo mantém a data original.
     */
    public void excluir(Instant quando) {
        if (deletedAt == null) {
            deletedAt = Objects.requireNonNull(quando, "quando");
        }
    }

    public boolean isExcluido() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Service other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
