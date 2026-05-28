package com.todaylunch.auction.infrastructure.config;

import com.todaylunch.auction.infrastructure.messaging.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic auctionBidFeeChargeRequestedTopic(
            @Value("${kafka.topic.auction-bid-fee-charge-requested.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.BID_FEE_CHARGE_REQUESTED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentBidFeeChargeSucceededTopic(
            @Value("${kafka.topic.payment-bid-fee-charge-succeeded.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.BID_FEE_CHARGE_COMPLETED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentBidFeeChargeFailedTopic(
            @Value("${kafka.topic.payment-bid-fee-charge-failed.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.BID_FEE_CHARGE_FAILED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionBidOutbidTopic(
            @Value("${kafka.topic.auction-bid-outbid.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.BID_OUTBID)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionBidFeeRefundRequestedTopic(
            @Value("${kafka.topic.auction-bid-fee-refund-requested.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.BID_FEE_REFUND_REQUESTED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionWonTopic(
            @Value("${kafka.topic.auction-won.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.AUCTION_WON)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionClosedTopic(
            @Value("${kafka.topic.auction-closed.partitions:8}") int partitions
    ) {
        return TopicBuilder.name(KafkaTopics.AUCTION_CLOSED)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
