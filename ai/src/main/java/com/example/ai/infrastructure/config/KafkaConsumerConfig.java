package com.example.ai.infrastructure.config;

import com.example.ai.infrastructure.messaging.kafka.InvalidProductEventPayloadException;
import com.example.ai.infrastructure.messaging.kafka.KafkaConsumerGroups;
import com.example.ai.infrastructure.messaging.kafka.ProductEventParseException;
import com.example.ai.infrastructure.messaging.kafka.dlq.ProductEventDlqPublisher;
import com.todaylunch.common.messaging.kafka.KafkaConsumerProps;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * AI 모듈 Kafka consumer 설정.
 * <p>
 * Product 이벤트는 payload를 문자열로 받은 뒤 내부 ObjectMapper로 직접 파싱하므로
 * StringDeserializer 기반 기본 listener factory를 명시적으로 등록한다.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> productEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return new DefaultKafkaConsumerFactory<>(
                KafkaConsumerProps.defaults(bootstrapServers, KafkaConsumerGroups.AI_PRODUCT_EMBEDDING_GROUP)
        );
    }

    @Bean
    public ConsumerRecordRecoverer productEventDlqRecoverer(
            ProductEventDlqPublisher productEventDlqPublisher
    ) {
        return (ConsumerRecord<?, ?> record, Exception exception) -> productEventDlqPublisher.publish(
                "productEventKafkaListener",
                record.topic(),
                record.value() == null ? "" : record.value().toString(),
                exception
        );
    }

    @Bean
    public DefaultErrorHandler productEventKafkaErrorHandler(
            ConsumerRecordRecoverer productEventDlqRecoverer,
            @Value("${ai.kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${ai.kafka.retry.max-attempts:3}") int maxAttempts,
            @Value("${ai.kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${ai.kafka.retry.max-interval-ms:5000}") long maxIntervalMs
    ) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxAttempts);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(productEventDlqRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                ProductEventParseException.class,
                InvalidProductEventPayloadException.class
        );
        return errorHandler;
    }

    @Bean(name = "productEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> productEventKafkaListenerContainerFactory(
            ConsumerFactory<String, String> productEventConsumerFactory,
            DefaultErrorHandler productEventKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productEventConsumerFactory);
        factory.setCommonErrorHandler(productEventKafkaErrorHandler);
        return factory;
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactory<String, String> productEventKafkaListenerContainerFactory
    ) {
        return productEventKafkaListenerContainerFactory;
    }
}
