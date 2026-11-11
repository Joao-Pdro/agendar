package br.com.agendar.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuário do sistema.
 *
 * <p>A senha é guardada **só** como hash BCrypt; a senha em claro nunca chega ao banco nem ao
 * objeto depois de criado.
 */
@Entity
@Table(name = "app_user", schema = "identity")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppUser() {
        // exigido pelo JPA
    }

    public AppUser(UUID id, UUID tenantId, String email, String passwordHash, String displayName, Role role) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email").toLowerCase(java.util.Locale.ROOT);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role");
        this.activo = true;
        this.tenantId = tenantId;
        if (role.isPlataforma() && tenantId != null) {
            throw new IllegalArgumentException("Operador da plataforma não pertence a tenant");
        }
        if (!role.isPlataforma() && tenantId == null) {
            throw new IllegalArgumentException("Usuário de tenant precisa de tenantId");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActivo() {
        return activo;
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
        if (!(o instanceof AppUser other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
