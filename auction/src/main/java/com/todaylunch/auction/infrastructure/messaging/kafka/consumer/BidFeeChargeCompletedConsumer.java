package com.todaylunch.auction.infrastructure.messaging.kafka.consumer;

import com.todaylunch.auction.application.service.BidUpdateService;
import com.todaylunch.auction.common.exception.application.BidNotFoundException;
import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.domain.repository.BidRepository;
import com.todaylunch.auction.infrastructure.messaging.kafka.KafkaTopics;
import com.todaylunch.auction.infrastructure.messaging.kafka.message.BidFeeChargeCompletedMessage;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * payment에서 수수료 차감이 성공했을 때 발행하는 이벤트를 소비한다.
 * 낙관락 충돌 시 최대 MAX_RETRY회 재시도 후 입찰 취소 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidFeeChargeCompletedConsumer {

    private static final int MAX_RETRY = 3;

    private final BidRepository bidRepository;
    private final BidUpdateService bidUpdateService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.BID_FEE_CHARGE_COMPLETED, containerFactory = "bidFeeChargeResultKafkaListenerContainerFactory")
    public void handle(String payload) throws Exception {
        EventEnvelope<BidFeeChargeCompletedMessage> envelope
                = objectMapper.readValue(payload, new TypeReference<>() {});
        BidFeeChargeCompletedMessage message = envelope.payload();

        Bid bid = bidRepository.findById(message.bidId())
                .orElseThrow(BidNotFoundException::new);

        if (bid.getStatus() != BidStatus.PENDING) {
            log.warn("중복 이벤트 또는 잘못된 상태 — 무시: bidId={}, status={}",
                    bid.getBidId(), bid.getStatus());
            return;
        }

        activateWithRetry(message.bidId(), message.auctionId());
    }

    private void activateWithRetry(UUID bidId, UUID auctionId) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                bidUpdateService.activate(bidId);
                log.info("Bid confirmed: bidId={}, auctionId={}", bidId, auctionId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("낙관락 충돌 — 재시도 {}/{}: bidId={}", attempt + 1, MAX_RETRY, bidId);
                try {
                    Thread.sleep(50L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        bidUpdateService.cancel(bidId);
    }
}
