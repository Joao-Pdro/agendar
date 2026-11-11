package br.com.agendar.identity.internal.security;

import br.com.agendar.identity.internal.Role;

import java.util.UUID;

/**
 * Usuário autenticado na requisição, extraído do JWT.
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

    /**
     * Prestador só enxerga e altera a própria agenda: seu id de usuário é também o id do
     * prestador.
     */
    public boolean canAccessProvider(UUID prestadorId) {
        if (canManageAnyProvider()) {
            return true;
        }
        return role == Role.PRESTADOR && userId.equals(prestadorId);
    }
}
