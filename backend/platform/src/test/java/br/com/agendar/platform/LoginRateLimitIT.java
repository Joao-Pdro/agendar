package br.com.agendar.platform;

import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.internal.Role;
import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Limite de tentativas de login (issue #21).
 *
 * <p>A aplicação sobe com {@code max-tentativas=3} para o teste não depender do padrão de 5.
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "agendar.security.login.max-tentativas=3")
@Testcontainers
class LoginRateLimitIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private br.com.agendar.identity.internal.security.LoginAttemptLimiter limiter;

    private UUID tenantId;
    private static final String EMAIL = "limite@teste.com";
    private static final String SENHA = "SenhaCorreta123";

    @BeforeEach
    void preparar() {
        // O contador vive no processo e não é limpo pela transação de teste.
        limiter.limparTudo();
        users.deleteAll();
        tenants.deleteAll();
        tenantId = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantId, "Empresa Limite", "empresa-" + tenantId));
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, EMAIL,
                passwordEncoder.encode(SENHA), "Limite", Role.ADMIN));
    }

    @Test
    void depoisDoLimiteOLoginDevolve429MesmoComSenhaCorreta() {
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Estourou o limite: agora nem a senha certa entra.
        ResponseEntity<String> bloqueado = login(SENHA);
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).contains("TOO_MANY_LOGIN_ATTEMPTS");
        assertThat(bloqueado.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void loginBemSucedidoZeraOContador() {
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(login(SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Contador zerado: dá para errar de novo duas vezes antes de bloquear.
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void limiteEporEmailOutroUsuarioNaoEBloqueado() {
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, "outro@teste.com",
                passwordEncoder.encode(SENHA), "Outro", Role.ADMIN));

        assertThat(login("outro@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> login(String senha) {
        return login(EMAIL, senha);
    }

    private ResponseEntity<String> login(String email, String senha) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", tenantId.toString(),
                        "email", email, "password", senha), headers),
                String.class);
    }
}
