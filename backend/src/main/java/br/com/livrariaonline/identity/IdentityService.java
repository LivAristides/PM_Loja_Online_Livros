package br.com.livrariaonline.identity;

import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import java.util.UUID;

@Service
public class IdentityService {
    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRecoveryService accountRecoveryService;
    private final AuditLogService audit;

    public IdentityService(IdentityRepository repository, PasswordEncoder passwordEncoder, AccountRecoveryService accountRecoveryService, AuditLogService audit) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.accountRecoveryService = accountRecoveryService;
        this.audit = audit;
    }

    @Transactional
    public AppUser registerCustomer(String name, String email, String password, String termsVersion) {
        try {
            AppUser user = repository.createCustomer(name, email, passwordEncoder.encode(password));
            repository.recordTermsConsent(user.id(), termsVersion);
            return user;
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEmailException();
        }
    }

    public UserDetails loadUserByUsername(String email) {
        AppUser user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        assertLoginAllowed(user);
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        return User.withUsername(user.email()).password(user.passwordHash()).authorities(authorities)
                .disabled(!user.enabled()).build();
    }

    @Transactional
    public void recordFailedLogin(String email) {
        repository.recordFailedLogin(email);
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        repository.findByEmail(email).ifPresent(user -> repository.resetFailedLoginAttempts(user.id()));
    }

    public void assertLoginAllowed(String email) {
        repository.findByEmail(email).ifPresent(this::assertLoginAllowed);
    }
    public void requestPasswordRecovery(String email) { accountRecoveryService.requestPasswordReset(email); }
    public void resetPassword(String token, String password) { accountRecoveryService.resetPassword(token, password); }

    public boolean requiresInitialPasswordChange(String email) { return repository.findByEmail(email).map(AppUser::passwordChangeRequired).orElse(false); }

    @Transactional
    public void completeInitialPasswordChange(String email, String password) {
        if (!requiresInitialPasswordChange(email)) throw new InitialPasswordChangeUnavailableException();
        repository.completeInitialPasswordChange(email, passwordEncoder.encode(password));
        audit.record(new AuditEvent("ADMINISTRATOR", email, "ADMIN_INITIAL_PASSWORD_CHANGED", "APP_USER", email, UUID.randomUUID(), "{}"));
    }

    private void assertLoginAllowed(AppUser user) {
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException();
        }
    }

    public static class DuplicateEmailException extends RuntimeException { }
    public static class AccountLockedException extends RuntimeException { }
    public static class InitialPasswordChangeUnavailableException extends IllegalArgumentException { InitialPasswordChangeUnavailableException() { super("A troca inicial de senha não está pendente."); } }
}
