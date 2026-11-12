package br.com.agendar.scheduling.internal.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Fuso do tenant.
 *
 * <p>Existe independente do expediente: a grade precisa saber em que fuso exibir mesmo antes de
 * o tenant configurar horários. Armazenamento e API seguem em UTC; este fuso só converte a hora
 * local do expediente.
 */
@Entity
@Table(name = "tenant_timezone", schema = "scheduling")
public class TenantTimezone {

    /** Padrão quando o tenant ainda não configurou. */
    public static final String PADRAO = "America/Sao_Paulo";

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    protected TenantTimezone() {
        // exigido pelo JPA
    }

    public TenantTimezone(UUID tenantId, String timezone) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.timezone = validarFuso(timezone);
    }

    public void alterar(String timezone) {
        this.timezone = validarFuso(timezone);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTimezone() {
        return timezone;
    }

    public ZoneId zona() {
        return ZoneId.of(timezone);
    }

    private static String validarFuso(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone é obrigatório");
        }
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("timezone inválido: " + timezone);
        }
        return timezone;
    }
}
