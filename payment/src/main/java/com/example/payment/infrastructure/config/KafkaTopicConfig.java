package com.example.payment.infrastructure.config;

import com.example.payment.infrastructure.messaging.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentBidFeeChargeSucceededTopic(
            @Value("${kafka.topic.payment-bid-fee-charge-succeeded.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.AUCTION_BID_FEE_CHARGE_SUCCEEDED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentBidFeeChargeFailedTopic(
            @Value("${kafka.topic.payment-bid-fee-charge-failed.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.AUCTION_BID_FEE_CHARGE_FAILED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
