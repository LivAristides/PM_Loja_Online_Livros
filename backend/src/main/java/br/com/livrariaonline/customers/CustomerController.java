package br.com.livrariaonline.customers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerController {
    private final CustomerRepository repository;
    private final String termsVersion;
    public CustomerController(CustomerRepository repository, @Value("${app.consent.terms-version}") String termsVersion) {
        this.repository = repository; this.termsVersion = termsVersion;
    }
    @GetMapping("/api/v1/me") public CustomerRepository.Profile profile(Authentication authentication) {
        return repository.findProfile(authentication.getName());
    }
    @PutMapping("/api/v1/me") public CustomerRepository.Profile updateProfile(Authentication authentication,
            @Valid @RequestBody ProfileInput input) { repository.updateName(authentication.getName(), input.name()); return repository.findProfile(authentication.getName()); }
    @GetMapping("/api/v1/addresses") public List<CustomerRepository.Address> addresses(Authentication authentication) { return repository.findAddresses(authentication.getName()); }
    @PostMapping("/api/v1/addresses") @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public CustomerRepository.Address addAddress(Authentication authentication, @Valid @RequestBody AddressInput input) {
        return repository.addAddress(authentication.getName(), new CustomerRepository.AddressInput(input.label(), input.recipientName(), input.postalCode(), input.street(), input.number(), input.complement(), input.neighborhood(), input.city(), input.state()));
    }
    @DeleteMapping("/api/v1/addresses/{id}") @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(Authentication authentication, @PathVariable UUID id) { repository.deleteAddress(authentication.getName(), id); }
    @GetMapping("/api/v1/me/consents") public ConsentResponse consents(Authentication authentication) { return new ConsentResponse(termsVersion, repository.marketingGranted(authentication.getName())); }
    @PutMapping("/api/v1/me/consents/marketing") public ConsentResponse marketing(Authentication authentication, @RequestBody MarketingInput input) {
        repository.recordConsent(authentication.getName(), "MARKETING", termsVersion, input.granted()); return new ConsentResponse(termsVersion, input.granted()); }
    public record ProfileInput(@NotBlank @Size(max = 120) String name) { }
    public record AddressInput(@NotBlank @Size(max = 60) String label, @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String postalCode, @NotBlank @Size(max = 160) String street,
            @NotBlank @Size(max = 20) String number, @Size(max = 100) String complement, @NotBlank @Size(max = 100) String neighborhood,
            @NotBlank @Size(max = 100) String city, @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state) { }
    public record MarketingInput(boolean granted) { }
    public record ConsentResponse(String termsVersion, boolean marketingGranted) { }
}
