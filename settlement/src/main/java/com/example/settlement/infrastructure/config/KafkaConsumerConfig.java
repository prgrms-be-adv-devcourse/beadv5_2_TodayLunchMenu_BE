package com.example.settlement.infrastructure.config;

import com.example.settlement.common.exception.CustomException;
import com.example.settlement.infrastructure.messaging.kafka.KafkaConsumerGroups;
import com.example.settlement.infrastructure.messaging.kafka.KafkaTopics;
import com.example.settlement.infrastructure.messaging.kafka.exception.SettlementKafkaValidationException;
import com.todaylunch.common.messaging.kafka.DlqErrorHandlerFactory;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * settlement 모듈 Kafka consumer(소비기) 설정을 담당한다.
 * <p>
 * 정산 원천/지급 결과 소비 경로에 retry(재시도)/backoff(백오프)/DLQ(사후처리큐) 정책을 적용한다.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> settlementCandidateCreatedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerGroups.SETTLEMENT_SERVICE);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 정산 원천 이벤트 소비 전용 리스너 팩토리를 생성한다.
     * <p>
     * 예외 발생 시 공통 에러 처리기로 재시도 후 DLQ 발행을 수행한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        settlementCandidateCreatedKafkaListenerContainerFactory(
            @Qualifier("settlementCandidateCreatedConsumerFactory")
            ConsumerFactory<String, String> settlementCandidateCreatedConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(settlementCandidateCreatedConsumerFactory);
        factory.setCommonErrorHandler(DlqErrorHandlerFactory.create(
                kafkaTemplate,
                KafkaTopics.SETTLEMENT_CANDIDATE_CREATED_DLQ,
                IllegalArgumentException.class,
                CustomException.class,
                SettlementKafkaValidationException.class));
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> sellerSettlementPayoutResultConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerGroups.SETTLEMENT_SERVICE);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 정산 지급 결과 이벤트 소비 전용 리스너 팩토리를 생성한다.
     * <p>
     * 예외 발생 시 공통 에러 처리기로 재시도 후 DLQ 발행을 수행한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        sellerSettlementPayoutResultKafkaListenerContainerFactory(
            @Qualifier("sellerSettlementPayoutResultConsumerFactory")
            ConsumerFactory<String, String> sellerSettlementPayoutResultConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sellerSettlementPayoutResultConsumerFactory);
        factory.setCommonErrorHandler(DlqErrorHandlerFactory.create(
                kafkaTemplate,
                KafkaTopics.SETTLEMENT_PAYOUT_RESULT_DLQ,
                IllegalArgumentException.class,
                CustomException.class,
                SettlementKafkaValidationException.class));
        return factory;
    }

}
