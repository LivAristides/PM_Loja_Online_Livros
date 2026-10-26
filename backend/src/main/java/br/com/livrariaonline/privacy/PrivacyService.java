package br.com.livrariaonline.privacy;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import br.com.livrariaonline.shared.correlation.CorrelationIdFilter;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PrivacyService {
    private final PrivacyRepository repository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    PrivacyService(PrivacyRepository repository, AuditLogService auditLogService, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    PrivacyRepository.PrivacyExport exportFor(String email) { return repository.exportFor(email); }

    @Transactional
    void anonymize(String email) {
        if (repository.hasActiveOrder(email)) throw new PrivacyRepository.ActiveOrderAnonymizationException();
        UUID userId = repository.userId(email);
        String anonymizedEmail = "anonymous-" + userId + "@invalid.local";
        repository.anonymize(userId, email, anonymizedEmail, passwordEncoder.encode(UUID.randomUUID().toString()));
        auditLogService.record(new AuditEvent("USER", userId.toString(), "PRIVACY_ANONYMIZATION", "CUSTOMER", userId.toString(),
                correlationId(), "{\"result\":\"anonymized\"}"));
    }

    List<PrivacyRepository.RetentionPolicy> retentionPolicies() { return repository.retentionPolicies(); }
    @Transactional void updateRetention(String key, int days) { repository.updateRetention(key, days); }

    private UUID correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        try { return current == null ? UUID.randomUUID() : UUID.fromString(current); }
        catch (IllegalArgumentException ignored) { return UUID.randomUUID(); }
    }
}
