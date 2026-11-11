package br.com.agendar.core.internal.web;

import br.com.agendar.identity.api.AuthenticatedUser;
import br.com.agendar.identity.api.Role;
import org.springframework.http.HttpStatus;

/**
 * Regra de escrita do catálogo: só o admin do tenant escreve. Recepção e prestador só leem.
 *
 * <p>Está em classe separada porque é a regra que mais tende a crescer (preço por prestador, por
 * exemplo) e é o que os testes precisam exercitar sem passar por HTTP.
 */
public final class CatalogoPermissao {

    private CatalogoPermissao() {
    }

    public static boolean podeEscrever(AuthenticatedUser user) {
        return user != null && user.role() == Role.ADMIN;
    }

    public static HttpStatus statusSeNaoPodeEscrever(AuthenticatedUser user) {
        if (user == null) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.FORBIDDEN;
    }
}
