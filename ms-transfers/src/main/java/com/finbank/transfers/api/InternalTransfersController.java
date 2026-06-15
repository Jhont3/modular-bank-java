package com.finbank.transfers.api;

import com.finbank.transfers.application.TransferUseCase;
import com.finbank.transfers.domain.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

/**
 * API interna (no enrutada por el gateway): compensación operativa de la saga.
 * Caso de uso: reverso regulatorio/fraude de una transferencia ya completada.
 */
@RestController
@RequestMapping("/internal/transfers")
@RequiredArgsConstructor
public class InternalTransfersController {

    private final TransferUseCase transferUseCase;

    @PostMapping("/{transferId}/compensate")
    public Transfer compensate(@PathVariable UUID transferId,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "OPERATIONAL_REVERSAL")
                                     : "OPERATIONAL_REVERSAL";
        return transferUseCase.compensate(transferId, reason);
    }
}
