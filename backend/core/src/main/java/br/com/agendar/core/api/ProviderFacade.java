package br.com.agendar.core.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do módulo {@code core} para consultar prestadores.
 *
 * <p>Existe porque a autorização da agenda precisa do vínculo usuário → prestador, e esse vínculo
 * mora em {@code core.provider}. Quem decide o que fazer com a resposta é o módulo que pergunta;
 * aqui só se responde o fato.
 */
public interface ProviderFacade {

    /**
     * Prestador ativo vinculado ao usuário, dentro do tenant.
     */
    Optional<UUID> prestadorDoUsuario(UUID tenantId, UUID userId);

    /**
     * Verdadeiro quando o prestador existe, está ativo e pertence ao tenant.
     */
    boolean existeNoTenant(UUID tenantId, UUID providerId);
}
