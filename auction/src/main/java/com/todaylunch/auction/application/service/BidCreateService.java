package com.todaylunch.auction.application.service;

import com.todaylunch.auction.application.event.OutboxEventPendingTrigger;
import com.todaylunch.auction.application.port.dto.request.BidFeeChargeRequest;
import com.todaylunch.auction.application.usecase.BidCreateUseCase;
import com.todaylunch.auction.domain.entity.Auction;
import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.entity.BidPolicy;
import com.todaylunch.auction.domain.entity.OutboxEvent;
import com.todaylunch.auction.domain.repository.AuctionRepository;
import com.todaylunch.auction.domain.repository.BidRepository;
import com.todaylunch.auction.domain.repository.OutboxEventRepository;
import com.todaylunch.auction.infrastructure.messaging.kafka.AuctionEventTypes;
import com.todaylunch.auction.infrastructure.messaging.kafka.KafkaTopics;
import com.todaylunch.auction.presentation.dto.request.BidPlaceRequest;
import com.todaylunch.auction.presentation.dto.response.BidResponse;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BidCreateService implements BidCreateUseCase {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public BidResponse place(UUID auctionId, UUID bidderId, BidPlaceRequest request) {

        Auction auction = auctionRepository.findById(auctionId);

        Optional<Bid> previousBid = bidRepository.findActiveByAuctionId(auctionId);

        auction.validatePendingBid(bidderId,
                                   request.bidPrice(),
                                   previousBid.map(Bid::getBidderId).orElse(null));

        Bid bid = Bid.placePending(auction,
                                   bidderId,
                                   request.bidPrice());

        Bid saved = bidRepository.save(bid);

        BigDecimal currentBidFee = BidPolicy.calculateBidFee(saved.getBidPrice());

        BidFeeChargeRequest event = new BidFeeChargeRequest(saved.getBidId(),
                                                            auction.getAuctionId(),
                                                            bidderId,
                                                            currentBidFee
        );

        EventEnvelope<BidFeeChargeRequest> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                AuctionEventTypes.AUCTION_BID_FEE_CHARGE_REQUESTED,
                "auction-service",
                event.auctionId(),
                event.highestBidderId(),
                Instant.now(),
                "mock-trace-id",
                event
        );

        outboxEventRepository.save(OutboxEvent.create(saved.getBidId(),
                                                      "BID",
                                                      "BID_FEE_CHARGE_REQUESTED",
                                                      KafkaTopics.BID_FEE_CHARGE_REQUESTED,
                                                      auction.getAuctionId().toString(),
                                                      serialize(envelope)
        ));

        eventPublisher.publishEvent(new OutboxEventPendingTrigger());

        log.info("Bid pending: bidId={}, auctionId={}, bidderId={}, bidPrice={}",
                 saved.getBidId(), auctionId, bidderId, saved.getBidPrice());

        return BidResponse.from(saved);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new IllegalStateException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
