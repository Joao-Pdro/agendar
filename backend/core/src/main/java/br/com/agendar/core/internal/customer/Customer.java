package br.com.agendar.core.internal.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cliente de um tenant.
 *
 * <p>Cliente não tem login no MVP: é cadastrado pela recepção ou pelo admin.
 *
 * <p>O documento (CPF ou CNPJ) é guardado só com dígitos, sem pontuação, para que a
 * unicidade por tenant não dependa de como foi digitado.
 *
 * <p>Soft delete: sai da lista, mas o agendamento antigo continua resolvendo quem contratou.
 */
@Entity
@Table(name = "customer", schema = "core")
public class Customer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "document", nullable = false)
    private String document;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Customer() {
        // exigido pelo JPA
    }

    public Customer(UUID id, UUID tenantId, String name, String document, String phone, String email) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = exigirNome(name);
        this.document = exigirDocumentoValido(document);
        this.phone = normalizarOpcional(phone);
        this.email = normalizarOpcional(email);
    }

    public void renomear(String name) {
        this.name = exigirNome(name);
    }

    public void alterarContato(String phone, String email) {
        if (phone != null) {
            this.phone = normalizarOpcional(phone);
        }
        if (email != null) {
            this.email = normalizarOpcional(email);
        }
    }

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

    public String getDocument() {
        return document;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
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

    private static String exigirDocumentoValido(String documento) {
        if (!Documento.valido(documento)) {
            throw new DocumentoInvalidoException(documento);
        }
        return Documento.normalizar(documento);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Documento com dígito verificador inválido. Vira 422 na borda web.
     */
    public static class DocumentoInvalidoException extends IllegalArgumentException {
        public DocumentoInvalidoException(String documento) {
            super("Documento inválido: " + documento);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Customer other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
