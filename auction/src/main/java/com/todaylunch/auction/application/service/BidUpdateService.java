package com.todaylunch.auction.application.service;

import com.todaylunch.auction.application.event.BidPlacedEvent;
import com.todaylunch.auction.common.exception.application.BidNotFoundException;
import com.todaylunch.auction.domain.entity.Auction;
import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.repository.AuctionRepository;
import com.todaylunch.auction.domain.repository.BidRepository;
import com.todaylunch.auction.infrastructure.messaging.kafka.publisher.KafkaBidFeeRefundRequestedPublisher;
import com.todaylunch.auction.infrastructure.messaging.kafka.publisher.KafkaBidOutbidEventPublisher;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidUpdateService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final KafkaBidOutbidEventPublisher bidOutbidEventPublisher;
    private final KafkaBidFeeRefundRequestedPublisher bidFeeRefundRequestedPublisher;


    @Transactional
    public void activate(UUID bidId) {
        Bid bid = bidRepository.findById(bidId).orElseThrow(BidNotFoundException::new);

        UUID auctionId = bid.getAuction().getAuctionId();
        Auction auction = auctionRepository.findById(auctionId);

        if (!auction.isHigherThanCurrentBid(bid.getBidPrice())) {
            bid.cancel();
            log.info("낙관락 재시도 중 더 높은 입찰 감지 — 입찰 취소: bidId={}", bidId);
            bidFeeRefundRequestedPublisher.publish(bidId, auctionId, bid.getBidderId());
            return;
        }

        Optional<Bid> previousActiveBid = bidRepository.findActiveByAuctionId(auctionId);

        bid.confirm();
        auction.updateHighestPrice(bid.getBidPrice());
        auction.extendTimeIfNearEnd(LocalDateTime.now());

        previousActiveBid.ifPresent(prev -> {
            prev.outbid();
            bidOutbidEventPublisher.publish(auctionId, prev.getBidderId());
        });

        applicationEventPublisher.publishEvent(new BidPlacedEvent(
                auctionId,
                bid.getBidderId(),
                bid.getBidPrice(),
                auction.getEndedAt()
        ));
    }

    @Transactional
    public void cancel(UUID bidId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(BidNotFoundException::new);
        bid.cancel();
        log.warn("낙관락 재시도 초과 — 입찰 취소: bidId={}", bidId);
        bidFeeRefundRequestedPublisher.publish(bidId, bid.getAuction().getAuctionId(), bid.getBidderId());
    }
}
