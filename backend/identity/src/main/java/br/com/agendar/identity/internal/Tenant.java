package br.com.agendar.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant (empresa cliente do sistema). Multi-tenant: todo dado de domínio pertence a um tenant.
 *
 * <p>O {@code customer_self_booking} é a flag que libera o cliente final a marcar horário
 * sozinho. Nasce {@code false} e é ligada por tenant.
 */
@Entity
@Table(name = "tenant", schema = "identity")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "customer_self_booking", nullable = false)
    private boolean customerSelfBooking;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Tenant() {
        // exigido pelo JPA
    }

    public Tenant(UUID id, String name, String slug) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.customerSelfBooking = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isCustomerSelfBooking() {
        return customerSelfBooking;
    }

    public void setCustomerSelfBooking(boolean customerSelfBooking) {
        this.customerSelfBooking = customerSelfBooking;
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
        if (!(o instanceof Tenant other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
