package com.finbank.transfers.infrastructure;

import com.finbank.transfers.application.TransferStateService;
import com.finbank.transfers.application.ports.AccountsPort;
import com.finbank.transfers.domain.Transfer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Recuperación de la saga ante el caso borde: el movimiento se aplicó en accounts
 * pero ms-transfers cayó antes de registrar COMPLETED. Como el movimiento es
 * idempotente y consultable por su key (= transferId), la reconciliación decide:
 *  - movimiento existe  → forward recovery: marcar COMPLETED y emitir el evento
 *  - movimiento no existe → el dinero nunca se movió: marcar FAILED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingTransferReconciler {

    private final TransferRepository transferRepository;
    private final TransferStateService state;
    private final AccountsPort accountsPort;

    @Scheduled(fixedDelayString = "${app.reconciler.poll-interval-ms:30000}")
    public void reconcileStuckPending() {
        Instant cutoff = Instant.now().minus(60, ChronoUnit.SECONDS);
        List<Transfer> stuck = transferRepository
            .findByStatusAndCreatedAtBefore(Transfer.TransferStatus.PENDING, cutoff);
        for (Transfer t : stuck) {
            try {
                AccountsPort.MovementResult movement = accountsPort.findMovement(t.getId().toString());
                if (movement != null) {
                    state.markCompleted(t.getId());
                    log.warn("Reconciler: stuck PENDING transfer recovered as COMPLETED. transferId={}", t.getId());
                } else {
                    state.markFailed(t.getId(), "RECONCILED_NO_MOVEMENT");
                    log.warn("Reconciler: stuck PENDING transfer marked FAILED (no movement). transferId={}", t.getId());
                }
            } catch (Exception e) {
                log.warn("Reconciler could not resolve transfer yet, will retry. transferId={} error={}",
                    t.getId(), e.getMessage());
            }
        }
    }
}
