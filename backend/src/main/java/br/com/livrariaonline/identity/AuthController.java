package br.com.livrariaonline.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identityService;
    private final AuthenticationManager authenticationManager;

    public AuthController(IdentityService identityService, AuthenticationManager authenticationManager) {
        this.identityService = identityService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        AppUser user = identityService.registerCustomer(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            identityService.assertLoginAllowed(request.email());
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            httpRequest.getSession(true);
            httpRequest.changeSessionId();
            SecurityContextHolder.getContext().setAuthentication(authentication);
            identityService.recordSuccessfulLogin(request.email());
            return ResponseEntity.ok(new SessionResponse(authentication.getName(), authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).toList()));
        } catch (IdentityService.AccountLockedException | LockedException exception) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("code", "account_locked",
                    "message", "Conta temporariamente bloqueada. Tente novamente em 15 minutos."));
        } catch (BadCredentialsException exception) {
            identityService.recordFailedLogin(request.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "invalid_credentials",
                    "message", "E-mail ou senha inválidos."));
        }
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        return new SessionResponse(authentication.getName(), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
    }

    public record RegistrationRequest(@NotBlank @Size(max = 120) String name,
                                      @NotBlank @Email @Size(max = 254) String email,
                                      @NotBlank @Size(min = 12, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record AuthResponse(String id, String email, List<String> roles) {
        static AuthResponse from(AppUser user) {
            return new AuthResponse(user.id().toString(), user.email(), user.roles().stream().map(Role::name).toList());
        }
    }
    public record SessionResponse(String email, List<String> roles) { }
}
