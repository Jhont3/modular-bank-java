package com.modularbank.modules.accounts.api;

import com.modularbank.modules.accounts.application.AccountsService;
import com.modularbank.modules.accounts.application.MovementsService;
import com.modularbank.modules.accounts.application.dto.AccountSummary;
import com.modularbank.modules.accounts.application.dto.MovementRequest;
import com.modularbank.modules.accounts.application.dto.MovementResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * API interna consumida exclusivamente por ms-transfers (red interna del compose).
 * No se expone a través del API Gateway.
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountsController {

    private final MovementsService movementsService;
    private final AccountsService accountsService;

    @PostMapping("/movements")
    public MovementResult executeMovement(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid MovementRequest request) {
        return movementsService.executeMovement(idempotencyKey, request);
    }

    @GetMapping("/by-user/{userId}")
    public List<AccountSummary> getAccountsByUser(@PathVariable UUID userId) {
        return accountsService.findByOwner(userId);
    }

    /** Consulta de la saga (reconciliación): ¿se aplicó ya el movimiento con esta key? */
    @GetMapping("/movements/{idempotencyKey}")
    public MovementResult getMovement(@PathVariable String idempotencyKey) {
        return movementsService.findMovement(idempotencyKey);
    }
}
