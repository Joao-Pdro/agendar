package br.com.agendar.identity.internal.security;

import br.com.agendar.identity.internal.AppUser;
import br.com.agendar.identity.internal.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Autenticação por e-mail e senha.
 */
@Service
@Transactional(readOnly = true)
public class AuthenticationService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(AppUserRepository users, PasswordEncoder passwordEncoder,
                                JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Login dentro de um tenant. O mesmo e-mail pode existir em tenants diferentes, então o
     * tenant precisa vir na requisição.
     */
    public Optional<String> login(UUID tenantId, String email, String password) {
        Optional<AppUser> user = users.findByTenantIdAndEmailIgnoreCase(tenantId, email);
        return user.filter(AppUser::isActivo)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .map(u -> jwtService.issue(toAuthenticated(u)));
    }

    /**
     * Login do operador da plataforma, que não pertence a tenant.
     */
    public Optional<String> loginPlataforma(String email, String password) {
        Optional<AppUser> user = users.findByTenantIdIsNullAndEmailIgnoreCase(email);
        return user.filter(AppUser::isActivo)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .map(u -> jwtService.issue(toAuthenticated(u)));
    }

    /**
     * Tentativas de login com e-mail inexistente em qualquer tenant em que a pessoa esteja.
     * Usado quando a requisição vem sem tenant informado.
     */
    public Optional<String> loginSemTenant(String email, String password) {
        List<AppUser> candidatos = users.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .filter(AppUser::isActivo)
                .toList();
        if (candidatos.size() != 1) {
            return Optional.empty();
        }
        AppUser user = candidatos.getFirst();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(jwtService.issue(toAuthenticated(user)));
    }

    private static AuthenticatedUser toAuthenticated(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getTenantId(), user.getRole());
    }
}
