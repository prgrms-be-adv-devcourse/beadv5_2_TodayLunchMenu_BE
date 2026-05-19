package com.todaylunch.auction.infrastructure.repository;

import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidJpaRepository extends JpaRepository<Bid, UUID> {

    @Query("""
            select b
            from Bid b
            where b.auction.auctionId = :auctionId
              and b.status = :status
            order by b.bidPrice desc
            limit 1
            """)
    Optional<Bid> findTopByAuctionIdAndStatus(
            @Param("auctionId") UUID auctionId,
            @Param("status") BidStatus status
    );

    @Query("""
            select b
            from Bid b
            where b.auction.auctionId = :auctionId
              and b.status in :statuses
            order by b.bidPrice desc
            limit 1
            """)
    Optional<Bid> findTopByAuctionIdAndStatusIn(
            @Param("auctionId") UUID auctionId,
            @Param("statuses") List<BidStatus> statuses
    );

    @Query("""
            select b
            from Bid b
            join fetch b.auction
            where b.auction.auctionId = :auctionId
            """)
    Page<Bid> findAllByAuctionId(@Param("auctionId") UUID auctionId, Pageable pageable);

    @Query("""
            select b.status, count(b)
            from Bid b
            where b.auction.auctionId = :auctionId
            group by b.status
            """)
    List<Object[]> countGroupByStatusForAuction(@Param("auctionId") UUID auctionId);
}
