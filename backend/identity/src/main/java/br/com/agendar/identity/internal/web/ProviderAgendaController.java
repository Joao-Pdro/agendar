package br.com.agendar.identity.internal.web;

import br.com.agendar.identity.api.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Stub da agenda de um prestador.
 *
 * <p>Existe só para validar a regra de acesso: prestador vê apenas a própria agenda; recepção e
 * admin veem a de qualquer prestador. A lista é vazia e o endpoint sai de {@code identity}
 * quando o módulo {@code scheduling} tiver a agenda de verdade (está no fora de escopo da #5).
 *
 * <p>Os {@code return} explícitos com o status são de propósito: a exceção de status acabava
 * convertida em 401 pelo tratamento de erros do Spring Security, e a regra precisa responder
 * 403 para deixar claro que o usuário está autenticado mas não tem permissão.
 */
@RestController
@RequestMapping("/prestadores")
class ProviderAgendaController {

    public record AgendaItem() {
    }

    @GetMapping("/{prestadorId}/agenda")
    ResponseEntity<List<AgendaItem>> agenda(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID prestadorId) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!user.canAccessProvider(prestadorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(List.of());
    }
}
