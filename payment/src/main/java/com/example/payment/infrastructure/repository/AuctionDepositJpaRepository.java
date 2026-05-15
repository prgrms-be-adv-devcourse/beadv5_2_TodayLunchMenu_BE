package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.AuctionDeposit;
import com.example.payment.domain.enumtype.AuctionDepositStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionDepositJpaRepository extends JpaRepository<AuctionDeposit, UUID> {

    Optional<AuctionDeposit> findByAuctionIdAndStatus(UUID auctionId, AuctionDepositStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuctionDeposit> findWithLockByAuctionIdAndStatus(UUID auctionId, AuctionDepositStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuctionDeposit> findWithLockByBidId(UUID bidId);
}
