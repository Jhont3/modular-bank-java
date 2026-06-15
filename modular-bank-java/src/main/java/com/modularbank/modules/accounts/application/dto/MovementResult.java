package com.modularbank.modules.accounts.application.dto;

import java.util.UUID;

public record MovementResult(UUID movementId, String status) {
    public static final String APPLIED = "APPLIED";
    public static final String DUPLICATE = "DUPLICATE";
}
