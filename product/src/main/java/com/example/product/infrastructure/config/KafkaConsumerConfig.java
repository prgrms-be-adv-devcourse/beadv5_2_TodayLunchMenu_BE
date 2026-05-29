package com.example.product.infrastructure.config;

import com.example.product.infrastructure.messaging.kafka.KafkaConsumerGroups;
import com.todaylunch.common.messaging.kafka.KafkaConsumerProps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:${cloud.kafka.bootstrap-servers:localhost:29092}}") String bootstrapServers
    ) {
        return new DefaultKafkaConsumerFactory<>(
                KafkaConsumerProps.defaults(bootstrapServers, KafkaConsumerGroups.PRODUCT_SERVICE)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
