package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.CardTransaction;
import com.example.payment.domain.enumtype.CardTransactionStatus;
import com.example.payment.domain.enumtype.CardTransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTransactionJpaRepository extends JpaRepository<CardTransaction, UUID> {

    List<CardTransaction> findByTransactionGroupId(UUID transactionGroupId);

    @Query("""
            select cardTransaction
            from CardTransaction cardTransaction
            where cardTransaction.referenceId in :orderItemIds
              and cardTransaction.transactionType = :transactionType
              and cardTransaction.transactionStatus = :transactionStatus
            """)
    List<CardTransaction> findSuccessfulPaymentsByOrderItemIds(
            @Param("orderItemIds") List<UUID> orderItemIds,
            @Param("transactionType") CardTransactionType transactionType,
            @Param("transactionStatus") CardTransactionStatus transactionStatus
    );

    @Query("""
            select cardTransaction
            from CardTransaction cardTransaction
            where cardTransaction.relatedTransactionId in :relatedTransactionIds
              and cardTransaction.transactionType = :transactionType
              and cardTransaction.transactionStatus = :transactionStatus
            """)
    List<CardTransaction> findSuccessfulCancelsByRelatedTransactionIds(
            @Param("relatedTransactionIds") List<UUID> relatedTransactionIds,
            @Param("transactionType") CardTransactionType transactionType,
            @Param("transactionStatus") CardTransactionStatus transactionStatus
    );
}
