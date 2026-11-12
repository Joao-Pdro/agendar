package br.com.agendar.identity;

import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import br.com.agendar.identity.api.Role;
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

    @Autowired
    private br.com.agendar.core.internal.provider.ProviderRepository prestadoresProvider;

    private UUID tenantA;
    private UUID tenantB;
    private UUID adminA;
    private UUID recepcaoA;
    private UUID prestadorA1;
    private UUID prestadorA2;
    private String senha;

    @BeforeEach
    void prepararDados() {
        prestadoresProvider.deleteAll();
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
        // O vínculo usuário → prestador vem do cadastro (#40): o id do prestador é diferente
        // do id do usuário, e mesmo assim o prestador acessa a própria agenda.
        UUID providerA1 = criarPrestadorVinculado(prestadorA1, "Prestador 1");
        UUID providerA2 = criarPrestadorVinculado(prestadorA2, "Prestador 2");

        assertThat(agenda(providerA1, prestadorA1)).isEqualTo(HttpStatus.OK);
        assertThat(agenda(providerA2, prestadorA1)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recepcaoAcessaAAgendaDeQualquerPrestadorDoTenant() {
        UUID providerA1 = criarPrestadorVinculado(prestadorA1, "Prestador 1");
        UUID providerA2 = criarPrestadorVinculado(prestadorA2, "Prestador 2");

        assertThat(agenda(providerA1, recepcaoA)).isEqualTo(HttpStatus.OK);
        assertThat(agenda(providerA2, recepcaoA)).isEqualTo(HttpStatus.OK);
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
        assertThat(rest.getForEntity("/providers/" + prestadorA1
                + "/appointments?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- helpers

    private UUID criarPrestadorVinculado(UUID userId, String nome) {
        // `providerA1` e afins são ids de USUÁRIO; o prestador tem id próprio e aponta para ele.
        var prestador = new br.com.agendar.core.internal.provider.Provider(
                UUID.randomUUID(), tenantA, nome, userId);
        prestadoresProvider.saveAndFlush(prestador);
        return prestador.getId();
    }

    private org.springframework.http.HttpStatus agenda(UUID providerId, UUID userId) {
        var usuario = users.findById(userId).orElseThrow();
        String token = tokenPara(userId, usuario.getTenantId(), usuario.getRole());
        var resposta = rest.exchange("/providers/" + providerId
                        + "/appointments?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(json(token)), String.class);
        return org.springframework.http.HttpStatus.valueOf(resposta.getStatusCode().value());
    }

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
