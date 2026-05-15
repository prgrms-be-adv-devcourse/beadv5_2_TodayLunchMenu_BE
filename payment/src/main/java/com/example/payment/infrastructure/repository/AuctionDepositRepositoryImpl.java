package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.AuctionDeposit;
import com.example.payment.domain.enumtype.AuctionDepositStatus;
import com.example.payment.domain.repository.AuctionDepositRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AuctionDepositRepositoryImpl implements AuctionDepositRepository {

    private final AuctionDepositJpaRepository auctionDepositJpaRepository;

    public AuctionDepositRepositoryImpl(AuctionDepositJpaRepository auctionDepositJpaRepository) {
        this.auctionDepositJpaRepository = auctionDepositJpaRepository;
    }

    @Override
    public AuctionDeposit save(AuctionDeposit auctionDeposit) {
        return auctionDepositJpaRepository.save(auctionDeposit);
    }

    @Override
    public Optional<AuctionDeposit> findByAuctionDepositId(UUID auctionDepositId) {
        return auctionDepositJpaRepository.findById(auctionDepositId);
    }

    @Override
    public Optional<AuctionDeposit> findHeldByAuctionId(UUID auctionId) {
        return auctionDepositJpaRepository.findByAuctionIdAndStatus(auctionId, AuctionDepositStatus.HELD);
    }

    @Override
    public Optional<AuctionDeposit> findHeldByAuctionIdForUpdate(UUID auctionId) {
        return auctionDepositJpaRepository.findWithLockByAuctionIdAndStatus(auctionId, AuctionDepositStatus.HELD);
    }

    @Override
    public Optional<AuctionDeposit> findByBidIdForUpdate(UUID bidId) {
        return auctionDepositJpaRepository.findWithLockByBidId(bidId);
    }
}
