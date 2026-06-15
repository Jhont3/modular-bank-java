package com.finbank.transfers.application;

import com.finbank.transfers.application.dto.TransferRequest;
import com.finbank.transfers.application.ports.AccountsPort;
import com.finbank.transfers.domain.Transfer;
import com.finbank.transfers.infrastructure.TransferRepository;
import com.finbank.transfers.infrastructure.TransferStreamBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

/**
 * Orquestador de la saga de transferencia (ADR-007):
 *
 *   1. Transfer PENDING (tx local)
 *   2. Movimiento débito+crédito atómico en accounts (remoto, idempotente,
 *      protegido por Circuit Breaker + Retry — la consistencia fuerte del
 *      dinero permanece en una sola transacción ACID del monolito)
 *   3. Transfer COMPLETED + evento TransferCompleted en el outbox (tx local)
 *
 * Fallo de negocio (403/404/422) → FAILED + TransferFailed (sin compensación:
 * el dinero no se movió). Fallo técnico / circuito abierto → FAILED + 503
 * controlado (Fallback). La compensación (movimiento inverso) existe para
 * reversar transferencias ya aplicadas — ver compensate().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferUseCase {

    private final TransferRepository transferRepository;
    private final TransferStateService state;
    private final AccountsPort accountsPort;
    private final TransferStreamBroadcaster broadcaster;

    public Transfer execute(UUID userId, TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Source and target accounts must be different");
        }

        Transfer transfer = state.createPending(userId, request.sourceAccountId(),
            request.targetAccountId(), request.amount(), request.reference());
        broadcaster.publish(transfer);
        // El transferId es la Idempotency-Key del movimiento: un reintento del
        // mismo transfer jamás produce un doble débito.
        String idempotencyKey = transfer.getId().toString();

        try {
            accountsPort.executeMovement(idempotencyKey, new AccountsPort.MovementCommand(
                userId, request.sourceAccountId(), request.targetAccountId(),
                request.amount(), request.reference(), "TRANSFER"));
        } catch (AccountsPort.AccountsBusinessException e) {
            log.warn("Transfer rejected by accounts. transferId={} status={} reason={}",
                transfer.getId(), e.getStatus(), e.getMessage());
            broadcaster.publish(state.markFailed(transfer.getId(), e.getMessage()));
            throw new ResponseStatusException(HttpStatus.valueOf(e.getStatus()), e.getMessage());
        } catch (AccountsPort.AccountsUnavailableException e) {
            log.error("Accounts unavailable, transfer failed without money movement. transferId={}",
                transfer.getId(), e);
            broadcaster.publish(state.markFailed(transfer.getId(), "ACCOUNTS_UNAVAILABLE"));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Transfers temporarily unavailable, please retry later");
        }

        Transfer completed = state.markCompleted(transfer.getId());
        broadcaster.publish(completed);
        log.info("Transfer completed. transferId={} amount={}", completed.getId(), completed.getAmount());
        return completed;
    }

    /**
     * Compensación de la saga: movimiento inverso idempotente en accounts
     * (ej. reverso operativo/regulatorio de una transferencia ya completada).
     */
    public Transfer compensate(UUID transferId, String reason) {
        Transfer t = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
        if (!Transfer.TransferStatus.COMPLETED.equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Only COMPLETED transfers can be compensated");
        }
        accountsPort.executeMovement("comp-" + transferId, new AccountsPort.MovementCommand(
            t.getUserId(), t.getTargetAccountId(), t.getSourceAccountId(),
            t.getAmount(), "Compensation of " + transferId, "COMPENSATION"));
        Transfer compensated = state.markCompensated(transferId, reason);
        broadcaster.publish(compensated);
        log.info("Transfer compensated. transferId={} reason={}", transferId, reason);
        return compensated;
    }

    public List<Transfer> getHistory(UUID userId, UUID accountId) {
        boolean owns = accountsPort.getAccountsByUser(userId).stream()
            .anyMatch(a -> a.id().equals(accountId));
        if (!owns) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to the user");
        }
        return transferRepository
            .findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(accountId, accountId);
    }
}
