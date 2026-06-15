package com.finbank.transfers.infrastructure;

import com.finbank.transfers.domain.OutboxEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publisher del Transactional Outbox: poll de eventos no publicados → Kafka.
 * Si el broker está caído, los eventos quedan retenidos en la tabla y se
 * publican al recuperarse (ningún evento de negocio se pierde — RNF-03).
 * key = transferId ⇒ orden garantizado por agregado dentro de la partición.
 * El header traceparent restaura la traza W3C original (Paso 4).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.transfers-topic}")
    private String topic;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try (Scope ignored = restoredContext(event.getTraceparent()).makeCurrent()) {
                // Con el contexto original activo, el agente OTel crea el span de
                // producer como hijo de la traza del request e inyecta él mismo el
                // header traceparent correcto (un único TraceId E2E — Paso 4).
                ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, event.getAggregateId().toString(), event.getPayload());
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                log.info("Outbox event published. eventId={} type={} aggregateId={}",
                    event.getId(), event.getType(), event.getAggregateId());
            } catch (Exception e) {
                log.warn("Broker unavailable, outbox event retained for retry. eventId={} error={}",
                    event.getId(), e.getMessage());
                break; // preserva el orden: no publicar eventos posteriores antes que este
            }
        }
    }

    /** Reconstruye el contexto W3C desde el traceparent persistido en la fila del outbox. */
    private Context restoredContext(String traceparent) {
        if (traceparent == null) {
            return Context.current();
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return Context.current();
        }
        SpanContext remote = SpanContext.createFromRemoteParent(
            parts[1], parts[2], TraceFlags.fromHex(parts[3], 0), TraceState.getDefault());
        return remote.isValid() ? Context.root().with(Span.wrap(remote)) : Context.current();
    }
}
