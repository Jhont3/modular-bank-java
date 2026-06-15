package com.modularbank.modules.accounts.application;

import com.modularbank.modules.accounts.application.dto.MovementRequest;
import com.modularbank.modules.accounts.application.dto.MovementResult;

public interface MovementsService {
    /**
     * Ejecuta débito + crédito como una única transacción ACID (consistencia fuerte).
     * Idempotente: la misma idempotencyKey nunca aplica el movimiento dos veces.
     */
    MovementResult executeMovement(String idempotencyKey, MovementRequest request);

    /** Devuelve el movimiento aplicado con esa key, o lanza 404 si no existe. */
    MovementResult findMovement(String idempotencyKey);
}
