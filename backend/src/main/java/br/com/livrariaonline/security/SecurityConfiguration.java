package br.com.livrariaonline.security;

import br.com.livrariaonline.identity.IdentityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(IdentityService identityService) {
        return identityService::loadUserByUsername;
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrfConfigurer -> csrfConfigurer.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/webhooks/payments"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/password-recovery", "/api/v1/auth/password-reset", "/api/v1/auth/admin-invitations/accept").permitAll()
                        .requestMatchers("/api/v1/webhooks/payments").permitAll()
                        .requestMatchers("/api/v1/books/**").permitAll()
                        .requestMatchers("/api/v1/checkout/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/auth/admin-invitations").hasRole("GENERAL_ADMINISTRATOR")
                        .requestMatchers("/api/v1/identity/access/admin/**").hasRole("GENERAL_ADMINISTRATOR")
                        .requestMatchers("/api/v1/identity/access/catalog/**").hasAnyRole("GENERAL_ADMINISTRATOR", "CATALOG_MANAGER")
                        .requestMatchers("/api/v1/admin/catalog/**", "/api/v1/admin/inventory/**")
                        .hasAnyRole("GENERAL_ADMINISTRATOR", "CATALOG_MANAGER")
                        .requestMatchers("/api/v1/admin/settings/**", "/api/v1/admin/shipping-zones/**").hasRole("GENERAL_ADMINISTRATOR")
                        .requestMatchers("/api/v1/admin/privacy/**").hasRole("GENERAL_ADMINISTRATOR")
                        .requestMatchers("/api/v1/cart/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/payments/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/identity/access/finance/**").hasAnyRole("GENERAL_ADMINISTRATOR", "FINANCE")
                        .requestMatchers("/api/v1/identity/access/support/**").hasAnyRole("GENERAL_ADMINISTRATOR", "SUPPORT")
                        .requestMatchers("/api/v1/auth/session", "/api/v1/identity/**", "/api/v1/me/**", "/api/v1/addresses/**", "/api/v1/privacy/**").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .securityContext(context -> context.requireExplicitSave(false))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").deleteCookies("LIVRARIA_SESSION", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
