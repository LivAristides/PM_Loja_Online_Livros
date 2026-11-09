package br.com.livrariaonline.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Schedules expiration through the persistent outbox, so a restart cannot lose a due reservation. */
@Component
class ReservationExpirationScheduler {
    private final PaymentRepository repository;
    private final ObjectMapper objectMapper;
    private final int pixReservationMinutes;
    private final int cardReservationMinutes;

    ReservationExpirationScheduler(PaymentRepository repository, ObjectMapper objectMapper,
                                   @Value("${app.payments.pix-reservation-minutes}") int pixReservationMinutes,
                                   @Value("${app.payments.card-reservation-minutes}") int cardReservationMinutes) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.pixReservationMinutes = pixReservationMinutes;
        this.cardReservationMinutes = cardReservationMinutes;
    }

    @Scheduled(fixedDelayString = "${app.payments.expiration-polling-delay-ms}")
    public void scheduleDueReservations() {
        for (PaymentRepository.ReservationCandidate candidate : repository.awaitingReservationExpiration()) {
            int duration = candidate.paymentMethod() == PaymentMethod.CARD ? cardReservationMinutes : pixReservationMinutes;
            Instant expiresAt = candidate.createdAt().plusSeconds(duration * 60L);
            if (expiresAt.isAfter(Instant.now())) continue;
            repository.scheduleExpiration(candidate.orderId(), expiresAt, payload(candidate.orderId()));
        }
    }

    private String payload(UUID orderId) {
        try { return objectMapper.writeValueAsString(new ExpirationPayload(orderId)); }
        catch (Exception exception) { throw new IllegalStateException("Falha ao agendar expiração.", exception); }
    }

    record ExpirationPayload(UUID orderId) { }
}
