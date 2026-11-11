package br.com.agendar.identity.internal.web;

import br.com.agendar.identity.internal.security.AuthenticationService;
import br.com.agendar.identity.internal.security.LoginAttemptLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthenticationService authenticationService;
    private final LoginAttemptLimiter limiter;

    AuthController(AuthenticationService authenticationService, LoginAttemptLimiter limiter) {
        this.authenticationService = authenticationService;
        this.limiter = limiter;
    }

    public record LoginRequest(
            UUID tenantId,
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token) {
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // O bloqueio é checado antes da senha: depois de estourar o limite, nem a senha correta
        // entra, que é o ponto de um limite de tentativas.
        if (limiter.bloqueado(request.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "900")
                    .body(problemaDeLimite());
        }

        Optional<String> token = request.tenantId() != null
                ? authenticationService.login(request.tenantId(), request.email(), request.password())
                : authenticationService.loginSemTenant(request.email(), request.password());

        if (token.isEmpty()) {
            limiter.registrarFalha(request.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        limiter.limpar(request.email());
        return ResponseEntity.ok(new LoginResponse(token.get()));
    }

    private static ProblemDetail problemaDeLimite() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Tente de novo mais tarde.");
        problema.setType(URI.create("https://agendar.local/problems/too-many-attempts"));
        problema.setTitle("Muitas tentativas de login");
        problema.setProperty("code", "TOO_MANY_LOGIN_ATTEMPTS");
        return problema;
    }
}
