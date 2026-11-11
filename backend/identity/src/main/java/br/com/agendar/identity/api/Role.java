package br.com.agendar.identity.api;

/**
 * Perfis do MVP (decididos na issue #1).
 *
 * <p>O operador da plataforma fica fora do tenant: cria tenants e liga/desliga a flag
 * {@code customer_self_booking}. Os demais pertencem a exatamente um tenant.
 */
public enum Role {

    OPERADOR_PLATAFORMA,
    ADMIN,
    RECEPCAO,
    PRESTADOR;

    public boolean isPlataforma() {
        return this == OPERADOR_PLATAFORMA;
    }
}
