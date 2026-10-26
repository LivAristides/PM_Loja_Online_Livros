package br.com.livrariaonline.privacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class PrivacyController {
    private final PrivacyService service;
    PrivacyController(PrivacyService service) { this.service = service; }

    @GetMapping("/privacy/export")
    PrivacyRepository.PrivacyExport export(Authentication authentication) { return service.exportFor(authentication.getName()); }

    @PostMapping("/privacy/anonymization")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void anonymize(Authentication authentication) { service.anonymize(authentication.getName()); }

    @GetMapping("/admin/privacy/retention")
    List<PrivacyRepository.RetentionPolicy> retention() { return service.retentionPolicies(); }

    @PutMapping("/admin/privacy/retention/{key}")
    PrivacyRepository.RetentionPolicy updateRetention(@PathVariable String key, @Valid @RequestBody RetentionInput input) {
        service.updateRetention(key, input.retentionDays());
        return service.retentionPolicies().stream().filter(policy -> policy.key().equals(key)).findFirst().orElseThrow();
    }

    record RetentionInput(@Min(1) int retentionDays) { }
}
