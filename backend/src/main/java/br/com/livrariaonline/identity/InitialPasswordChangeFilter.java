package br.com.livrariaonline.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import br.com.livrariaonline.shared.error.ApiErrorWriter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InitialPasswordChangeFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_PATHS = Set.of("/api/v1/auth/session", "/api/v1/auth/csrf", "/api/v1/auth/admin-password-change", "/api/v1/auth/logout");
    private final IdentityService identityService;
    private final ApiErrorWriter errors;

    public InitialPasswordChangeFilter(IdentityService identityService, ApiErrorWriter errors) { this.identityService = identityService; this.errors = errors; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && identityService.requiresInitialPasswordChange(authentication.getName()) && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            errors.write(response, HttpServletResponse.SC_FORBIDDEN, "initial_password_change_required", "Altere a senha inicial para continuar.");
            return;
        }
        chain.doFilter(request, response);
    }
}
