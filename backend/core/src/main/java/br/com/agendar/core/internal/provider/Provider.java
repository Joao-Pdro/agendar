package br.com.agendar.core.internal.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Prestador de serviço de um tenant.
 *
 * <p>O {@code userId} aponta para o usuário do módulo {@code identity}, mas sem FK e sem
 * relacionamento JPA: módulos não compartilham chave nem navegam entre si. Pode ser nulo para um
 * prestador que ainda não tem login.
 *
 * <p>Soft delete: sai da lista, mas o agendamento antigo continua resolvendo quem executou.
 */
@Entity
@Table(name = "provider", schema = "core")
public class Provider {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Provider() {
        // exigido pelo JPA
    }

    public Provider(UUID id, UUID tenantId, String name, UUID userId) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = exigirNome(name);
        this.userId = userId;
    }

    public void renomear(String name) {
        this.name = exigirNome(name);
    }

    public void vincularUsuario(UUID userId) {
        this.userId = userId;
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

    public UUID getUserId() {
        return userId;
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

    private static String exigirNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        return nome.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Provider other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
