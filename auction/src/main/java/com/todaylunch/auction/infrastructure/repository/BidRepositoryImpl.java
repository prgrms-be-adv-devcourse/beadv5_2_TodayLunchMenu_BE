package com.todaylunch.auction.infrastructure.repository;

import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.domain.repository.BidRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BidRepositoryImpl implements BidRepository {

    private final BidJpaRepository jpaRepository;

    @Override
    public Bid save(Bid bid) {
        return jpaRepository.save(bid);
    }

    @Override
    public Optional<Bid> findById(UUID bidId) {
        return jpaRepository.findById(bidId);
    }

    @Override
    public Optional<Bid> findActiveByAuctionId(UUID auctionId) {
        return jpaRepository.findTopByAuctionIdAndStatus(auctionId, BidStatus.ACTIVE);
    }

    @Override
    public Optional<Bid> findCurrentValidByAuctionId(UUID auctionId) {
        return jpaRepository.findTopByAuctionIdAndStatusIn(
                auctionId, List.of(BidStatus.ACTIVE, BidStatus.OUTBID));
    }

    @Override
    public Page<Bid> findAllByAuctionId(UUID auctionId, Pageable pageable) {
        return jpaRepository.findAllByAuctionId(auctionId, pageable);
    }

    @Override
    public Map<BidStatus, Long> countByStatusForAuction(UUID auctionId) {
        return jpaRepository.countGroupByStatusForAuction(auctionId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (BidStatus) row[0],
                        row -> (Long) row[1]
                ));
    }
}
