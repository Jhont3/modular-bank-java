package com.finbank.transfers.application;

import com.finbank.transfers.domain.Transfer;
import com.finbank.transfers.infrastructure.OutboxRepository;
import com.finbank.transfers.infrastructure.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Transiciones de estado de la saga. Cada transición que publica un evento lo
 * inserta en el outbox DENTRO de la misma transacción local (Outbox Pattern):
 * el estado y su evento son atómicos.
 */
@Service
@RequiredArgsConstructor
public class TransferStateService {

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final TransferEvents events;

    @Transactional
    public Transfer createPending(UUID userId, UUID sourceAccountId, UUID targetAccountId,
                                  java.math.BigDecimal amount, String reference) {
        return transferRepository.save(Transfer.builder()
            .userId(userId)
            .sourceAccountId(sourceAccountId)
            .targetAccountId(targetAccountId)
            .amount(amount)
            .reference(reference)
            .build());
    }

    @Transactional
    public Transfer markCompleted(UUID transferId) {
        Transfer t = transferRepository.findById(transferId).orElseThrow();
        t.setStatus(Transfer.TransferStatus.COMPLETED);
        t = transferRepository.save(t);
        outboxRepository.save(events.completed(t));
        return t;
    }

    @Transactional
    public Transfer markFailed(UUID transferId, String reason) {
        Transfer t = transferRepository.findById(transferId).orElseThrow();
        t.setStatus(Transfer.TransferStatus.FAILED);
        t.setFailureReason(truncate(reason));
        t = transferRepository.save(t);
        outboxRepository.save(events.failed(t, truncate(reason)));
        return t;
    }

    @Transactional
    public Transfer markCompensated(UUID transferId, String reason) {
        Transfer t = transferRepository.findById(transferId).orElseThrow();
        t.setStatus(Transfer.TransferStatus.COMPENSATED);
        t.setFailureReason(truncate(reason));
        t = transferRepository.save(t);
        outboxRepository.save(events.compensated(t, truncate(reason)));
        return t;
    }

    // failure_reason es varchar(255); un upstream podría devolver un body arbitrario
    private static String truncate(String reason) {
        return (reason != null && reason.length() > 255) ? reason.substring(0, 255) : reason;
    }
}
