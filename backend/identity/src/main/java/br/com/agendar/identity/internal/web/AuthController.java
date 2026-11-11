package br.com.agendar.identity.internal.web;

import br.com.agendar.identity.internal.security.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthenticationService authenticationService;

    AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public record LoginRequest(
            UUID tenantId,
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token) {
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Optional<String> token = request.tenantId() != null
                ? authenticationService.login(request.tenantId(), request.email(), request.password())
                : authenticationService.loginSemTenant(request.email(), request.password());

        return token.map(LoginResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Credenciais inválidas"));
    }
}
