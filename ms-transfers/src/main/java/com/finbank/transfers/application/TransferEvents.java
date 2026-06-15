package com.finbank.transfers.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finbank.transfers.domain.OutboxEvent;
import com.finbank.transfers.domain.Transfer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * Construye los eventos de dominio con envelope tipo CloudEvents (ADR-010).
 * Versionamiento: en el campo type (…TransferCompleted.v1) y en el tópico (…events.v1).
 * El traceparent W3C se captura aquí (contexto de la request) y viaja en el outbox
 * para que el publisher lo propague como header de Kafka (trazabilidad E2E, Paso 4).
 */
@Component
@RequiredArgsConstructor
public class TransferEvents {

    public static final String SOURCE = "ms-transfers";

    private final ObjectMapper objectMapper;

    public OutboxEvent completed(Transfer t) {
        return build("finbank.transfers.TransferCompleted.v1", t, null);
    }

    public OutboxEvent failed(Transfer t, String reason) {
        return build("finbank.transfers.TransferFailed.v1", t, reason);
    }

    public OutboxEvent compensated(Transfer t, String reason) {
        return build("finbank.transfers.TransferCompensated.v1", t, reason);
    }

    private OutboxEvent build(String type, Transfer t, String reason) {
        UUID eventId = UUID.randomUUID();

        ObjectNode data = objectMapper.createObjectNode();
        data.put("transferId", t.getId().toString());
        data.put("userId", t.getUserId().toString());
        data.put("sourceAccountId", t.getSourceAccountId().toString());
        data.put("targetAccountId", t.getTargetAccountId().toString());
        data.put("amount", t.getAmount().toPlainString());
        data.put("currency", "USD");
        data.put("reference", t.getReference());
        data.put("status", t.getStatus());
        if (reason != null) {
            data.put("reason", reason);
        }

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("specversion", "1.0");
        envelope.put("id", eventId.toString());
        envelope.put("type", type);
        envelope.put("source", SOURCE);
        envelope.put("time", Instant.now().toString());
        String traceparent = currentTraceparent();
        if (traceparent != null) {
            envelope.put("traceparent", traceparent);
        }
        envelope.set("data", data);

        return OutboxEvent.builder()
            .id(eventId)
            .aggregateId(t.getId())
            .type(type)
            .payload(envelope.toString())
            .traceparent(traceparent)
            .build();
    }

    private String currentTraceparent() {
        SpanContext ctx = Span.current().getSpanContext();
        if (!ctx.isValid()) {
            return null;
        }
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-" + ctx.getTraceFlags().asHex();
    }
}
