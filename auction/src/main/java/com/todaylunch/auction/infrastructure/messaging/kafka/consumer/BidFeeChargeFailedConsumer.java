package com.todaylunch.auction.infrastructure.messaging.kafka.consumer;

import com.todaylunch.auction.common.exception.application.BidNotFoundException;
import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.domain.repository.AuctionRepository;
import com.todaylunch.auction.domain.repository.BidRepository;
import com.todaylunch.auction.infrastructure.messaging.kafka.KafkaTopics;
import com.todaylunch.auction.infrastructure.messaging.kafka.message.BidFeeChargeFailedMessage;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * payment에서 수수료 차감이 실패했을 때 발행하는 이벤트를 소비한다.
 * Bid PENDING → CANCELED 전이.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidFeeChargeFailedConsumer {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.BID_FEE_CHARGE_FAILED, containerFactory = "bidFeeChargeResultKafkaListenerContainerFactory")
    @Transactional
    public void handle(String payload) throws Exception {
        EventEnvelope<BidFeeChargeFailedMessage> envelope
                = objectMapper.readValue(payload, new TypeReference<>() {});
        BidFeeChargeFailedMessage message = envelope.payload();

        Bid bid = bidRepository.findById(message.bidId()).orElseThrow(BidNotFoundException::new);

        if (!bid.isPending()) {
            log.warn("중복 이벤트 또는 잘못된 상태 — 무시: bidId={}, status={}", bid.getBidId(), bid.getStatus());
            return;
        }

        bid.cancel();

        UUID auctionId = message.auctionId();
        BigDecimal previousHighestPrice = bidRepository.findCurrentValidByAuctionId(auctionId)
                .map(Bid::getBidPrice)
                .orElse(null);

        auctionRepository.findById(auctionId)
                .rollbackHighestPrice(previousHighestPrice);

        log.warn("Bid canceled via kafka: bidId={}, errorCode={}, errorMessage={}",
                bid.getBidId(), message.errorCode(), message.errorMessage());

        messagingTemplate.convertAndSend(
                "/topic/users/" + bid.getBidderId(),
                "입찰에 실패했습니다. 잔액을 확인해주세요."
        );
    }
}
