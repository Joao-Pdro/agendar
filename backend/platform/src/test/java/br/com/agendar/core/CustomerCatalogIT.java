package br.com.agendar.core;

import br.com.agendar.core.internal.customer.CustomerRepository;
import br.com.agendar.identity.api.Role;
import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cadastro de clientes (#31).
 */
@SpringBootTest(
        classes = br.com.agendar.platform.AgendarApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CustomerCatalogIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenants;
    @Autowired private AppUserRepository users;
    @Autowired private CustomerRepository clientes;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private UUID tenantA;
    private UUID tenantB;
    private String adminA;
    private String recepcaoA;
    private String prestadorA;
    private String adminB;
    private static final String SENHA = "SenhaForte123";
    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";

    @BeforeEach
    void preparar() {
        clientes.deleteAll();
        users.deleteAll();
        tenants.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        tenants.saveAndFlush(new Tenant(tenantA, "A", "cu-a-" + tenantA));
        tenants.saveAndFlush(new Tenant(tenantB, "B", "cu-b-" + tenantB));

        adminA = criarUsuario(tenantA, "adm-a@t.com", Role.ADMIN);
        recepcaoA = criarUsuario(tenantA, "rec-a@t.com", Role.RECEPCAO);
        prestadorA = criarUsuario(tenantA, "pre-a@t.com", Role.PRESTADOR);
        adminB = criarUsuario(tenantB, "adm-b@t.com", Role.ADMIN);
    }

    @Test
    void recepcaoECriaClienteComCpfValido() throws Exception {
        ResponseEntity<String> resposta = criar(recepcaoA, "Maria", CPF);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode corpo = objectMapper.readTree(resposta.getBody());
        assertThat(corpo.get("document").asText()).isEqualTo(CPF);
        assertThat(corpo.get("deletedAt").isNull()).isTrue();
    }

    @Test
    void aceitaCnpjECpfFormatado() {
        assertThat(criar(recepcaoA, "Empresa X", CNPJ).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criar(adminA, "João", "529.982.247-25").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void documentoInvalidoDevolve422() {
        ResponseEntity<String> resposta = criar(recepcaoA, "Fulano", "11111111111");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("DOCUMENTO_INVALIDO");
    }

    @Test
    void documentoDuplicadoNoMesmoTenantDevolve409() {
        assertThat(criar(recepcaoA, "Maria", CPF).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicado = criar(recepcaoA, "Outra Maria", CPF);

        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicado.getBody()).contains("DOCUMENTO_DUPLICADO");
    }

    @Test
    void mesmoDocumentoEmOutroTenantEPermitido() {
        assertThat(criar(adminA, "Maria", CPF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criar(adminB, "Maria", CPF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void prestadorSoLe() throws Exception {
        String id = objectMapper.readTree(criar(adminA, "Maria", CPF).getBody()).get("id").asText();

        assertThat(criar(prestadorA, "Outro", CNPJ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/customers", prestadorA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/customers/" + id, prestadorA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void buscaPorNomeEDocumento() throws Exception {
        criar(adminA, "Maria Silva", CPF);
        criar(adminA, "Empresa Zeta", CNPJ);

        JsonNode porNome = objectMapper.readTree(get("/customers?q=maria", adminA).getBody());
        assertThat(porNome).hasSize(1);
        assertThat(porNome.get(0).get("name").asText()).isEqualTo("Maria Silva");

        JsonNode porDocumento = objectMapper.readTree(get("/customers?q=11222333", adminA).getBody());
        assertThat(porDocumento).hasSize(1);
        assertThat(porDocumento.get(0).get("name").asText()).isEqualTo("Empresa Zeta");
    }

    @Test
    void isolamentoDeTenant() throws Exception {
        String idA = objectMapper.readTree(criar(adminA, "Maria", CPF).getBody()).get("id").asText();

        assertThat(objectMapper.readTree(get("/customers", adminB).getBody())).isEmpty();
        assertThat(get("/customers/" + idA, adminB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void softDeleteSaiDAListaLiberaODocumentoEMantemODetalhe() throws Exception {
        String id = objectMapper.readTree(criar(adminA, "Maria", CPF).getBody()).get("id").asText();

        ResponseEntity<String> exclusao = rest.exchange("/customers/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers(adminA)), String.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(objectMapper.readTree(get("/customers", adminA).getBody())).isEmpty();
        assertThat(get("/customers/" + id, adminA).getStatusCode()).isEqualTo(HttpStatus.OK);

        // O documento volta a ficar livre para um novo cadastro.
        assertThat(criar(adminA, "Maria de novo", CPF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ---------------------------------------------------------------- helpers

    private String criarUsuario(UUID tenantId, String email, Role role) {
        users.saveAndFlush(new AppUser(UUID.randomUUID(), tenantId, email,
                passwordEncoder.encode(SENHA), email, role));
        return email;
    }

    private ResponseEntity<String> criar(String email, String nome, String documento) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("name", nome);
        corpo.put("document", documento);
        return rest.postForEntity("/customers", new HttpEntity<>(corpo, headers(email)), String.class);
    }

    private ResponseEntity<String> get(String path, String email) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(email)), String.class);
    }

    private HttpHeaders headers(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (email != null) {
            headers.setBearerAuth(tokenDe(email));
        }
        return headers;
    }

    private String tokenDe(String email) {
        AppUser user = users.findAll().stream().filter(u -> u.getEmail().equals(email))
                .findFirst().orElseThrow();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("tenantId", user.getTenantId().toString(),
                        "email", email, "password", SENHA), h), String.class);
        try {
            return objectMapper.readTree(resposta.getBody()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
