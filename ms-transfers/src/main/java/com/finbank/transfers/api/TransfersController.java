package com.finbank.transfers.api;

import com.finbank.transfers.application.TransferUseCase;
import com.finbank.transfers.application.dto.TransferRequest;
import com.finbank.transfers.domain.Transfer;
import com.finbank.transfers.infrastructure.TransferStreamBroadcaster;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mismo contrato HTTP que el módulo transfers del monolito (requisito Paso 1/2):
 * POST /transfers y GET /transfers?accountId=… con las mismas respuestas.
 */
@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransfersController {

    private final TransferUseCase transferUseCase;
    private final TransferStreamBroadcaster broadcaster;

    /** Stream SSE de estado en tiempo real para el microfrontend (punto extra). */
    @GetMapping("/stream")
    public SseEmitter stream(Authentication auth) {
        return broadcaster.subscribe((UUID) auth.getPrincipal());
    }

    @PostMapping
    public ResponseEntity<?> executeTransfer(@RequestBody @Valid TransferRequest request,
                                             Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        try {
            Transfer transfer = transferUseCase.execute(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Error"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getHistory(@RequestParam UUID accountId, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        try {
            List<Transfer> history = transferUseCase.getHistory(userId, accountId);
            return ResponseEntity.ok(history);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }
}
