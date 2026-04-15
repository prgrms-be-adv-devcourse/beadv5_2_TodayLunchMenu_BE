package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.CardTransaction;
import com.example.payment.domain.enumtype.CardTransactionStatus;
import com.example.payment.domain.enumtype.CardTransactionType;
import com.example.payment.domain.repository.CardTransactionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CardTransactionRepositoryImpl implements CardTransactionRepository {

    private final CardTransactionJpaRepository cardTransactionJpaRepository;

    public CardTransactionRepositoryImpl(CardTransactionJpaRepository cardTransactionJpaRepository) {
        this.cardTransactionJpaRepository = cardTransactionJpaRepository;
    }

    @Override
    public CardTransaction save(CardTransaction cardTransaction) {
        return cardTransactionJpaRepository.save(cardTransaction);
    }

    @Override
    public List<CardTransaction> saveAll(Iterable<CardTransaction> cardTransactions) {
        return cardTransactionJpaRepository.saveAll(cardTransactions);
    }

    @Override
    public Optional<CardTransaction> findByCardTransactionId(UUID cardTransactionId) {
        return cardTransactionJpaRepository.findById(cardTransactionId);
    }

    @Override
    public List<CardTransaction> findByTransactionGroupId(UUID transactionGroupId) {
        return cardTransactionJpaRepository.findByTransactionGroupId(transactionGroupId);
    }

    @Override
    public List<CardTransaction> findSuccessfulPaymentsByOrderItemIds(List<UUID> orderItemIds) {
        return cardTransactionJpaRepository.findSuccessfulPaymentsByOrderItemIds(
                orderItemIds,
                CardTransactionType.PAYMENT,
                CardTransactionStatus.SUCCESS
        );
    }

    @Override
    public List<CardTransaction> findSuccessfulCancelsByRelatedTransactionIds(List<UUID> relatedTransactionIds) {
        return cardTransactionJpaRepository.findSuccessfulCancelsByRelatedTransactionIds(
                relatedTransactionIds,
                CardTransactionType.CANCEL,
                CardTransactionStatus.SUCCESS
        );
    }
}
