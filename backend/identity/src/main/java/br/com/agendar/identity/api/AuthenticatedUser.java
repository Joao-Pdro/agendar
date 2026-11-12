package br.com.agendar.identity.api;

import java.util.UUID;

/**
 * Usuário autenticado na requisição, extraído do JWT.
 *
 * <p>Deliberadamente **não** decide acesso a agenda: o vínculo usuário → prestador vive no
 * módulo {@code core}, não aqui. Quem precisa da regra pergunta ao
 * {@code br.com.agendar.core.api.ProviderFacade} e combina com {@link #canManageAnyProvider()}.
 *
 * @param userId    id do usuário
 * @param tenantId  tenant do usuário; {@code null} para o operador da plataforma
 * @param role      perfil
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, Role role) {

    public boolean isPlataforma() {
        return role.isPlataforma();
    }

    /**
     * Admin do tenant vê tudo dentro do próprio tenant.
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Recepção e admin agendam para qualquer prestador do tenant.
     */
    public boolean canManageAnyProvider() {
        return role == Role.ADMIN || role == Role.RECEPCAO;
    }

    public boolean isPrestador() {
        return role == Role.PRESTADOR;
    }
}
