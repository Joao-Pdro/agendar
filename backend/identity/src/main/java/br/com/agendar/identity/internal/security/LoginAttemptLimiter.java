package br.com.agendar.identity.internal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tentativas de login por e-mail.
 *
 * <p>Sem isso, dá para testar senhas indefinidamente em {@code POST /auth/login}. Depois de
 * {@code max-tentativas} falhas do mesmo e-mail dentro da janela, o login é bloqueado mesmo com a
 * senha correta até a janela expirar.
 *
 * <p>O estado vive na memória do processo. Com mais de uma instância, cada uma tem o seu
 * contador — o limite real seria multiplicado pelo número de instâncias. Persistir em banco ou
 * Redis é a evolução natural, fora do escopo desta issue.
 */
@Component
public class LoginAttemptLimiter {

    private final int maxTentativas;
    private final Duration janela;
    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(
            @Value("${agendar.security.login.max-tentativas:5}") int maxTentativas,
            @Value("${agendar.security.login.janela:PT15M}") Duration janela) {
        if (maxTentativas < 1) {
            throw new IllegalStateException("agendar.security.login.max-tentativas precisa ser >= 1");
        }
        this.maxTentativas = maxTentativas;
        this.janela = janela;
    }

    /**
     * Indica se o e-mail já estourou o limite. Não registra nada.
     */
    public boolean bloqueado(String email) {
        Deque<Instant> doEmail = falhas.get(chave(email));
        if (doEmail == null) {
            return false;
        }
        synchronized (doEmail) {
            limparAntigas(doEmail);
            return doEmail.size() >= maxTentativas;
        }
    }

    /**
     * Registra uma tentativa falha para o e-mail.
     */
    public void registrarFalha(String email) {
        Deque<Instant> doEmail = falhas.computeIfAbsent(chave(email), k -> new ArrayDeque<>());
        synchronized (doEmail) {
            limparAntigas(doEmail);
            doEmail.addLast(Instant.now());
        }
    }

    /**
     * Zera o contador do e-mail, chamado depois de um login bem-sucedido.
     */
    public void limpar(String email) {
        falhas.remove(chave(email));
    }

    /**
     * Zera todos os contadores. Usado nos testes para isolar um caso do outro.
     */
    public void limparTudo() {
        falhas.clear();
    }

    private void limparAntigas(Deque<Instant> doEmail) {
        Instant limite = Instant.now().minus(janela);
        while (!doEmail.isEmpty() && doEmail.peekFirst().isBefore(limite)) {
            doEmail.removeFirst();
        }
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
