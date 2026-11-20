package br.com.livrariaonline.identity;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AdministrativeUserController {
    private final IdentityRepository repository;
    private final AdminInvitationService invitations;
    private final IdentityService identityService;
    private final AuditLogService audit;

    AdministrativeUserController(IdentityRepository repository, AdminInvitationService invitations, IdentityService identityService, AuditLogService audit) {
        this.repository = repository; this.invitations = invitations; this.identityService = identityService; this.audit = audit;
    }

    @GetMapping("/api/v1/admin/users")
    List<AdministrativeUserResponse> users() { return repository.administrativeUsers().stream().map(AdministrativeUserResponse::from).toList(); }

    @PostMapping("/api/v1/admin/users/invitations")
    InvitationResponse invite(@Valid @RequestBody InvitationInput input, Authentication authentication) {
        String token = invitations.invite(input.email(), input.role(), authentication.getName());
        audit.record(new AuditEvent("GENERAL_ADMINISTRATOR", authentication.getName(), "ADMIN_INVITATION_CREATED", "APP_USER", input.email().toLowerCase(), UUID.randomUUID(),
                "{\"role\":\"" + input.role().name() + "\"}"));
        return new InvitationResponse(token);
    }

    @DeleteMapping("/api/v1/admin/users/{id}")
    void deactivate(@PathVariable UUID id, Authentication authentication) {
        AppUser target = repository.administrativeUsers().stream().filter(user -> user.id().equals(id)).findFirst().orElseThrow(AdministrativeUserNotFoundException::new);
        if (target.email().equalsIgnoreCase(authentication.getName())) throw new SelfDeactivationException();
        repository.deactivateAdministrativeUser(id);
        audit.record(new AuditEvent("GENERAL_ADMINISTRATOR", authentication.getName(), "ADMIN_USER_DEACTIVATED", "APP_USER", id.toString(), UUID.randomUUID(), "{}"));
    }

    @PostMapping("/api/v1/auth/admin-password-change")
    void changeInitialPassword(@Valid @RequestBody InitialPasswordInput input, Authentication authentication) {
        identityService.completeInitialPasswordChange(authentication.getName(), input.password());
    }

    record InvitationInput(@NotBlank @Email @Size(max = 254) String email, @NotNull Role role) { }
    record InitialPasswordInput(@NotBlank @Size(min = 12, max = 72) String password) { }
    record InvitationResponse(String invitationToken) { }
    record AdministrativeUserResponse(UUID id, String name, String email, List<String> roles, boolean enabled, boolean passwordChangeRequired) {
        static AdministrativeUserResponse from(AppUser user) { return new AdministrativeUserResponse(user.id(), user.name(), user.email(),
                user.roles().stream().map(Role::name).toList(), user.enabled(), user.passwordChangeRequired()); }
    }
    static final class AdministrativeUserNotFoundException extends IllegalArgumentException { AdministrativeUserNotFoundException() { super("Usuário administrativo não encontrado."); } }
    static final class SelfDeactivationException extends IllegalArgumentException { SelfDeactivationException() { super("Não é permitido desativar o próprio usuário."); } }
}
