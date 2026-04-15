package com.example.payment.domain.repository;

import com.example.payment.domain.entity.CardTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardTransactionRepository {

    CardTransaction save(CardTransaction cardTransaction);

    List<CardTransaction> saveAll(Iterable<CardTransaction> cardTransactions);

    Optional<CardTransaction> findByCardTransactionId(UUID cardTransactionId);

    List<CardTransaction> findByTransactionGroupId(UUID transactionGroupId);

    List<CardTransaction> findSuccessfulPaymentsByOrderItemIds(List<UUID> orderItemIds);

    List<CardTransaction> findSuccessfulCancelsByRelatedTransactionIds(List<UUID> relatedTransactionIds);
}
