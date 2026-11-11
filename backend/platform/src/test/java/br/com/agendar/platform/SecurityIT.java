package br.com.agendar.identity;

import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.internal.Role;
import br.com.agendar.identity.internal.Tenant;
import br.com.agendar.identity.internal.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Critérios de aceite da #5, exercitados por HTTP de verdade.
 */
@SpringBootTest(classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityIT {

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
    private ObjectMapper objectMapper;

    private UUID tenantA;
    private UUID tenantB;
    private UUID adminA;
    private UUID recepcaoA;
    private UUID prestadorA1;
    private UUID prestadorA2;
    private String senha;

    @BeforeEach
    void prepararDados() {
        users.deleteAll();
        tenants.deleteAll();

        senha = "SenhaForte123";
        tenantA = criarTenant("Empresa A", "empresa-a");
        tenantB = criarTenant("Empresa B", "empresa-b");

        adminA = criarUsuario(tenantA, "admin@a.com", Role.ADMIN);
        recepcaoA = criarUsuario(tenantA, "recepcao@a.com", Role.RECEPCAO);
        prestadorA1 = criarUsuario(tenantA, "p1@a.com", Role.PRESTADOR);
        prestadorA2 = criarUsuario(tenantA, "p2@a.com", Role.PRESTADOR);
        criarUsuario(tenantB, "admin@b.com", Role.ADMIN);
    }

    @Test
    void loginRetornaJwtComTenantIdERole() throws Exception {
        String token = login(tenantA, "admin@a.com");

        JsonNode claims = claimsDe(token);
        assertThat(claims.get("tenantId").asText()).isEqualTo(tenantA.toString());
        assertThat(claims.get("role").asText()).isEqualTo("ADMIN");
        assertThat(claims.get("sub").asText()).isEqualTo(adminA.toString());
    }

    @Test
    void senhaFicaArmazenadaComoHashBcryptDiferenteDaSenha() {
        AppUser user = users.findById(adminA).orElseThrow();

        assertThat(user.getPasswordHash()).isNotEqualTo(senha);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(senha, user.getPasswordHash())).isTrue();
    }

    @Test
    void loginComSenhaErradaResponde401() {
        ResponseEntity<String> response = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", tenantA.toString(),
                        "email", "admin@a.com", "password", "errada"), json()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminAlteraAFlagDoTenant() {
        ResponseEntity<String> response = patch("/tenant/settings", adminA,
                Map.of("customerSelfBooking", true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenants.findById(tenantA).orElseThrow().isCustomerSelfBooking()).isTrue();
    }

    @Test
    void prestadorERecepcaoRecebem403AoAlterarAFlag() {
        assertThat(patch("/tenant/settings", prestadorA1, Map.of("customerSelfBooking", true))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch("/tenant/settings", recepcaoA, Map.of("customerSelfBooking", true))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(tenants.findById(tenantA).orElseThrow().isCustomerSelfBooking()).isFalse();
    }

    @Test
    void prestadorAcessaAPropriaAgendaEMasNaoADeOutro() {
        assertThat(get("/prestadores/" + prestadorA1 + "/agenda", prestadorA1).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(get("/prestadores/" + prestadorA2 + "/agenda", prestadorA1).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recepcaoAcessaAAgendaDeQualquerPrestadorDoTenant() {
        assertThat(get("/prestadores/" + prestadorA1 + "/agenda", recepcaoA).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/prestadores/" + prestadorA2 + "/agenda", recepcaoA).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void usuarioDeUmTenantNaoEnxergaDadosDeOutro() throws Exception {
        String tokenA = login(tenantA, "admin@a.com");
        String tokenB = login(tenantB, "admin@b.com");

        // O token de B altera a configuração de B, nunca a de A.
        ResponseEntity<String> resposta = rest.exchange("/tenant/settings", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("customerSelfBooking", true), json(tokenB)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenants.findById(tenantB).orElseThrow().isCustomerSelfBooking()).isTrue();
        assertThat(tenants.findById(tenantA).orElseThrow().isCustomerSelfBooking()).isFalse();

        assertThat(claimsDe(tokenA).get("tenantId").asText()).isEqualTo(tenantA.toString());
        assertThat(claimsDe(tokenB).get("tenantId").asText()).isEqualTo(tenantB.toString());
    }

    @Test
    void requisicaoSemTokenResponde401() {
        assertThat(rest.getForEntity("/prestadores/" + prestadorA1 + "/agenda", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- helpers

    private UUID criarTenant(String nome, String slug) {
        UUID id = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(id, nome, slug));
        return id;
    }

    private UUID criarUsuario(UUID tenantId, String email, Role role) {
        UUID id = UUID.randomUUID();
        users.saveAndFlush(new AppUser(id, tenantId, email,
                passwordEncoder.encode(senha), email, role));
        return id;
    }

    private String login(UUID tenantId, String email) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", tenantId.toString(),
                        "email", email, "password", senha), json()),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private JsonNode claimsDe(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(decoded);
    }

    private ResponseEntity<String> get(String path, UUID userId) {
        Role role = users.findById(userId).orElseThrow().getRole();
        UUID tenantId = users.findById(userId).orElseThrow().getTenantId();
        return rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(json(tokenPara(userId, tenantId, role))), String.class);
    }

    private ResponseEntity<String> patch(String path, UUID userId, Map<String, Object> body) {
        Role role = users.findById(userId).orElseThrow().getRole();
        UUID tenantId = users.findById(userId).orElseThrow().getTenantId();
        return rest.exchange(path, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("customerSelfBooking", body.get("customerSelfBooking")),
                        json(tokenPara(userId, tenantId, role))), String.class);
    }

    private String tokenPara(UUID userId, UUID tenantId, Role role) {
        String email = users.findById(userId).orElseThrow().getEmail();
        try {
            return login(tenantId, email);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders json(String bearer) {
        HttpHeaders headers = json();
        headers.setBearerAuth(bearer);
        return headers;
    }
}
