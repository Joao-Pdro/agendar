package br.com.agendar.identity.internal.security;

import br.com.agendar.identity.api.AuthenticatedUser;
import br.com.agendar.identity.api.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emissão e leitura do JWT.
 *
 * <p>O token carrega {@code tenantId} e {@code role}: é assim que o filtro sabe o tenant sem
 * confiar em nada do corpo da requisição.
 */
@Component
public class JwtService {

    static final String CLAIM_TENANT = "tenantId";
    static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(
            @Value("${agendar.security.jwt.secret}") String secret,
            @Value("${agendar.security.jwt.ttl:PT8H}") Duration ttl) {
        // Sem valor padrão: o perfil de produção não define o segredo, então subir sem JWT_SECRET
        // falha aqui, antes de a aplicação aceitar qualquer requisição.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "agendar.security.jwt.secret não configurado. Defina JWT_SECRET no ambiente"
                            + " (perfil de produção) ou use o perfil dev.");
        }
        // HS256 exige pelo menos 256 bits de chave.
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "agendar.security.jwt.secret precisa ter pelo menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.userId().toString())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
        if (user.tenantId() != null) {
            builder.claim(CLAIM_TENANT, user.tenantId().toString());
        }
        return builder.compact();
    }

    /**
     * Lê e valida o token. Token inválido, expirado ou com claims faltando devolve vazio.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            String tenant = claims.get(CLAIM_TENANT, String.class);
            UUID tenantId = tenant == null ? null : UUID.fromString(tenant);

            return Optional.of(new AuthenticatedUser(userId, tenantId, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
