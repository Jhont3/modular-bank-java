package com.finbank.notifications.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finbank.notifications.application.NotificationsService;
import com.finbank.notifications.domain.NotificationType;
import com.finbank.notifications.domain.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reemplaza la llamada in-process notificationsService.send() que hacía el módulo
 * transfers del monolito: ahora la interacción llega como evento asíncrono (Paso 2).
 * Idempotente (eventId en processed_events) — el broker entrega at-least-once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventsConsumer {

    private final NotificationsService notificationsService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${app.kafka.transfers-topic:finbank.transfers.events.v1}",
        groupId = "ms-notifications")
    @Transactional
    public void onTransferEvent(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        UUID eventId = UUID.fromString(event.get("id").asText());
        String type = event.get("type").asText();

        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event ignored (idempotency). eventId={} type={}", eventId, type);
            return;
        }

        JsonNode data = event.get("data");
        UUID userId = UUID.fromString(data.get("userId").asText());

        NotificationType notificationType;
        if (type.contains("TransferCompleted")) {
            notificationType = NotificationType.TRANSFER_SENT;
        } else if (type.contains("TransferFailed")) {
            notificationType = NotificationType.TRANSFER_FAILED;
        } else if (type.contains("TransferCompensated")) {
            notificationType = NotificationType.TRANSFER_COMPENSATED;
        } else {
            log.warn("Unknown event type, skipping. eventId={} type={}", eventId, type);
            return;
        }

        // Mismo payload que generaba el monolito para TRANSFER_SENT (contrato preservado)
        Map<String, String> payload = new HashMap<>();
        payload.put("amount", data.get("amount").asText());
        payload.put("targetAccountId", data.get("targetAccountId").asText());
        if (data.hasNonNull("reason")) {
            payload.put("reason", data.get("reason").asText());
        }

        notificationsService.send(userId, notificationType, payload);
        processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
        log.info("Notification created from broker event. eventId={} type={} userId={}",
            eventId, notificationType, userId);
    }
}
