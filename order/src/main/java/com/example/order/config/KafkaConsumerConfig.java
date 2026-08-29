package com.example.order.config;

import com.example.order.infrastructure.kafka.KafkaConsumerGroups;
import com.example.order.infrastructure.kafka.event.AuctionWonEvent;
import com.example.order.infrastructure.kafka.event.PaymentResultEvent;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private JsonMapper objectMapper;

    private Map<String, Object> commonProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerGroups.ORDER_GROUP);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<PaymentResultEvent>> paymentResultConsumerFactory() {
        JacksonJsonDeserializer<EventEnvelope<PaymentResultEvent>> deserializer =
                new JacksonJsonDeserializer<>(new TypeReference<>() {}, objectMapper);
        deserializer.addTrustedPackages("*");
        deserializer.ignoreTypeHeaders();
        return new DefaultKafkaConsumerFactory<>(commonProps(), new StringDeserializer(), deserializer);
    }

    @Bean(name = "paymentListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentResultEvent>> paymentListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<PaymentResultEvent>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentResultConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, EventEnvelope<AuctionWonEvent>> auctionWonConsumerFactory() {
        JacksonJsonDeserializer<EventEnvelope<AuctionWonEvent>> deserializer =
                new JacksonJsonDeserializer<>(new TypeReference<>() {}, objectMapper);
        deserializer.addTrustedPackages("*");
        deserializer.ignoreTypeHeaders();
        return new DefaultKafkaConsumerFactory<>(commonProps(), new StringDeserializer(), deserializer);
    }

    @Bean(name = "auctionListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<AuctionWonEvent>> auctionListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<AuctionWonEvent>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionWonConsumerFactory());
        return factory;
    }
}
