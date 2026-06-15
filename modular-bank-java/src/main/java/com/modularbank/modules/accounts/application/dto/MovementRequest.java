package com.modularbank.modules.accounts.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record MovementRequest(
    @NotNull UUID userId,
    @NotNull UUID sourceAccountId,
    @NotNull UUID targetAccountId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String reference,
    @NotNull MovementType type
) {
    public enum MovementType {
        TRANSFER,
        COMPENSATION
    }
}
