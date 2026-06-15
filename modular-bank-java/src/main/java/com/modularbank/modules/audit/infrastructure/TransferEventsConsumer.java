package com.modularbank.modules.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.modules.audit.domain.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El módulo audit permanece en el monolito pero reacciona a los eventos de negocio
 * publicados por ms-transfers vía Kafka (requisito Paso 2/3 del reto).
 * Consumidor idempotente: eventId es la idempotency key (tabla audit.processed_events).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventsConsumer {

    private final AuditService auditService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${app.kafka.transfers-topic:finbank.transfers.events.v1}",
        groupId = "monolith-audit",
        autoStartup = "${audit.kafka.enabled:true}")
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

        String action;
        if (type.contains("TransferCompleted")) {
            action = "TRANSFER_EXECUTED";
        } else if (type.contains("TransferFailed")) {
            action = "TRANSFER_FAILED";
        } else if (type.contains("TransferCompensated")) {
            action = "TRANSFER_COMPENSATED";
        } else {
            log.warn("Unknown event type, skipping. eventId={} type={}", eventId, type);
            return;
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("transferId", data.get("transferId").asText());
        metadata.put("amount", data.get("amount").asText());
        metadata.put("sourceAccountId", data.get("sourceAccountId").asText());
        metadata.put("targetAccountId", data.get("targetAccountId").asText());
        metadata.put("status", data.get("status").asText());
        metadata.put("eventId", eventId.toString());

        auditService.record(userId, action, metadata);
        processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
        log.info("Audit entry recorded from broker event. eventId={} action={} transferId={}",
            eventId, action, metadata.get("transferId"));
    }
}
