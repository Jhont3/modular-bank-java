package com.modularbank.shared.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.common.TopicPartition;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Resiliencia del consumidor: Retry con backoff exponencial y, agotados los
 * reintentos, el mensaje se publica en el Dead Letter Topic (<topic>.dlt)
 * para no bloquear la partición (patrones Retry+Backoff y DLQ del reto).
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (ConsumerRecord<?, ?> record, Exception ex) ->
                new TopicPartition(record.topic() + ".dlt", record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(8_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
