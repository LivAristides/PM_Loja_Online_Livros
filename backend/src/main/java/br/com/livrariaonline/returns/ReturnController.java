package br.com.livrariaonline.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReturnController {
    private final ReturnService service;
    ReturnController(ReturnService service) { this.service = service; }

    @PostMapping("/api/v1/orders/{orderId}/returns")
    ReturnResponse request(Authentication authentication, @PathVariable UUID orderId, @Valid @RequestBody RequestInput input) {
        return ReturnResponse.from(service.request(authentication.getName(), orderId, input.reason()));
    }
    @GetMapping("/api/v1/returns/{id}")
    ReturnResponse detail(Authentication authentication, @PathVariable UUID id) { return ReturnResponse.from(service.detail(authentication.getName(), id)); }
    @PostMapping("/api/v1/returns/{id}/posting")
    ReturnResponse post(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody PostingInput input) {
        return ReturnResponse.from(service.post(authentication.getName(), id, input.trackingCode()));
    }
    @PutMapping("/api/v1/admin/returns/{id}/decision")
    ReturnResponse decide(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody DecisionInput input) {
        return ReturnResponse.from(service.decide(authentication.getName(), id, input.status(), input.justification()));
    }

    record RequestInput(@NotNull ReturnReason reason) { }
    record PostingInput(@NotBlank @Size(max = 80) String trackingCode) { }
    record DecisionInput(@NotNull ReturnStatus status, @Size(max = 300) String justification) { }
    record ReturnResponse(UUID id, UUID orderId, String reason, String status, Instant requestedAt, Instant reviewDueAt, Instant approvedAt,
                          String refusalJustification, Instant postingDueAt, Instant postedAt, String returnTrackingCode) {
        static ReturnResponse from(ReturnRequest request) { return new ReturnResponse(request.id(), request.orderId(), request.reason().name(), request.status().name(),
                request.requestedAt(), request.reviewDueAt(), request.approvedAt(), request.refusalJustification(), request.postingDueAt(),
                request.postedAt(), request.returnTrackingCode()); }
    }
}
