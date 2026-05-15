package com.todaylunch.auction.infrastructure.messaging.kafka.publisher;

import com.todaylunch.auction.infrastructure.messaging.kafka.AuctionEventTypes;
import com.todaylunch.auction.infrastructure.messaging.kafka.KafkaTopics;
import com.todaylunch.auction.infrastructure.messaging.kafka.message.BidFeeRefundRequestedMessage;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaBidFeeRefundRequestedPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(UUID bidId, UUID auctionId, UUID bidderId) {
        try {
            BidFeeRefundRequestedMessage payload = new BidFeeRefundRequestedMessage(bidId, auctionId, bidderId);
            EventEnvelope<BidFeeRefundRequestedMessage> envelope = new EventEnvelope<>(
                    UUID.randomUUID(),
                    AuctionEventTypes.AUCTION_BID_FEE_REFUND_REQUESTED,
                    "auction-service",
                    auctionId,
                    bidderId,
                    Instant.now(),
                    "mock-trace-id",
                    payload
            );
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(KafkaTopics.BID_FEE_REFUND_REQUESTED, bidId.toString(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("bid-fee-refund 이벤트 발행 실패: bidId={}, auctionId={}", bidId, auctionId, ex);
                        } else {
                            log.info("bid-fee-refund 이벤트 발행 성공: bidId={}, auctionId={}", bidId, auctionId);
                        }
                    });
        } catch (JacksonException e) {
            log.error("bid-fee-refund 이벤트 직렬화 실패: bidId={}", bidId, e);
        }
    }
}
