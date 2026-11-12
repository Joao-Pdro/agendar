package br.com.agendar.scheduling.internal.dependencias;

import br.com.agendar.core.api.ProviderFacade;
import br.com.agendar.identity.api.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Regra de quem pode ver e agendar na agenda de um prestador.
 *
 * <p>O vínculo usuário → prestador vive em {@code core}, então a decisão pergunta ao
 * {@link ProviderFacade} em vez de comparar o id do usuário com o id do prestador — que são
 * coisas diferentes (#40). Nenhuma tabela de {@code core} é lida daqui: só a porta pública.
 */
@Component
public class AcessoAgenda {

    private final ProviderFacade prestadores;

    public AcessoAgenda(ProviderFacade prestadores) {
        this.prestadores = prestadores;
    }

    public boolean podeAcessar(AuthenticatedUser user, UUID providerId) {
        if (user == null) {
            return false;
        }
        if (user.canManageAnyProvider()) {
            return true;
        }
        if (!user.isPrestador()) {
            return false;
        }
        return prestadores.prestadorDoUsuario(user.tenantId(), user.userId())
                .map(providerId::equals)
                .orElse(false);
    }
}
