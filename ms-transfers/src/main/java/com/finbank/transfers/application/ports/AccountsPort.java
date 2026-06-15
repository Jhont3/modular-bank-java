package com.finbank.transfers.application.ports;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida (hexagonal) hacia el módulo accounts del monolito remanente.
 * El movimiento débito+crédito es UNA operación atómica del lado de accounts:
 * la consistencia fuerte del dinero no se distribuye (decisión ADR-007).
 */
public interface AccountsPort {

    /** @throws AccountsBusinessException error de negocio (403/404/422) — no reintentar
     *  @throws AccountsUnavailableException fallo técnico o circuito abierto */
    MovementResult executeMovement(String idempotencyKey, MovementCommand command);

    /** @return el movimiento si ya fue aplicado, o null si no existe (reconciliación de la saga) */
    MovementResult findMovement(String idempotencyKey);

    List<AccountSummary> getAccountsByUser(UUID userId);

    record MovementCommand(
        UUID userId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String reference,
        String type // TRANSFER | COMPENSATION
    ) {}

    record MovementResult(UUID movementId, String status) {}

    record AccountSummary(UUID id, String accountNumber, BigDecimal balance) {}

    class AccountsBusinessException extends RuntimeException {
        private final int status;
        public AccountsBusinessException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() { return status; }
    }

    class AccountsUnavailableException extends RuntimeException {
        public AccountsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
