package com.finbank.transfers.infrastructure;

import com.finbank.transfers.domain.Transfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Difusión SSE del estado de transferencias por usuario (punto extra MFE):
 * el microfrontend de transfers abre GET /transfers/stream y recibe cada
 * transición de la saga (PENDING/COMPLETED/FAILED/COMPENSATED) en tiempo real.
 * Estado en memoria: si el pod se reinicia, el cliente EventSource reconecta solo.
 */
@Component
@Slf4j
public class TransferStreamBroadcaster {

    private final Map<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout; el heartbeat mantiene viva la conexión
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = emittersByUser.get(userId);
            if (list != null) {
                list.remove(emitter);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        log.info("SSE subscriber added. userId={}", userId);
        return emitter;
    }

    public void publish(Transfer transfer) {
        List<SseEmitter> list = emittersByUser.get(transfer.getUserId());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("transfer-status").data(Map.of(
                    "transferId", transfer.getId().toString(),
                    "status", transfer.getStatus(),
                    "amount", transfer.getAmount(),
                    "sourceAccountId", transfer.getSourceAccountId().toString(),
                    "targetAccountId", transfer.getTargetAccountId().toString(),
                    "failureReason", transfer.getFailureReason() == null ? "" : transfer.getFailureReason())));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }
    }

    /** Mantiene vivas las conexiones a través de proxies (nginx/gateway). */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emittersByUser.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
    }
}
