package com.example.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.payment.application.dto.OrderPaymentCommand;
import com.example.payment.application.dto.OrderPaymentLineCommand;
import com.example.payment.application.dto.OrderPaymentResult;
import com.example.payment.common.exception.InvalidOrderPaymentRequestException;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.entity.Wallet;
import com.example.payment.domain.repository.EscrowRepository;
import com.example.payment.domain.repository.WalletRepository;
import com.example.payment.domain.repository.WalletTransactionRepository;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.TimeProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentService 테스트")
class OrderPaymentServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private EscrowRepository escrowRepository;

    @Mock
    private IdentifierGenerator identifierGenerator;

    @Mock
    private TimeProvider timeProvider;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    private UUID orderId;
    private UUID buyerMemberId;
    private UUID sellerMemberId;
    private UUID buyerWalletId;
    private UUID escrowId;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        buyerMemberId = UUID.randomUUID();
        sellerMemberId = UUID.randomUUID();
        buyerWalletId = UUID.randomUUID();
        escrowId = UUID.randomUUID();
        now = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
    }

    @Nested
    @DisplayName("payOrder() 테스트")
    class PayOrder {

        @Test
        @DisplayName("정상 결제 시 구매자 금액을 차감하고 seller별 escrow를 생성한다")
        void payOrder_success_decreasesBuyerBalanceAndCreatesEscrow() {
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    12_000L,
                    List.of(new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 12_000L)),
                    null
            );
            Wallet buyerWallet = Wallet.create(buyerWalletId, buyerMemberId, 20_000L, now, now);

            given(escrowRepository.findAllByOrderId(orderId)).willReturn(List.of());
            given(walletRepository.findByMemberId(buyerMemberId)).willReturn(java.util.Optional.of(buyerWallet));
            given(timeProvider.now()).willReturn(now);
            given(identifierGenerator.generateUuid()).willReturn(UUID.randomUUID(), escrowId);
            given(walletRepository.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(walletTransactionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(escrowRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

            OrderPaymentResult result = orderPaymentService.payOrder(command);

            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.buyerWalletId()).isEqualTo(buyerWalletId);
            assertThat(result.escrowIds()).containsExactly(escrowId);
            assertThat(result.paidAmount()).isEqualTo(12_000L);
            assertThat(result.buyerWalletBalance()).isEqualTo(8_000L);
            verify(walletRepository).save(any(Wallet.class));
            verify(walletTransactionRepository).save(any());
            verify(escrowRepository).saveAll(any());
        }

        @Test
        @DisplayName("seller가 여러 명이면 buyer는 한 번만 차감하고 escrow는 여러 건을 생성한다")
        void payOrder_multiSeller_createsMultipleEscrows() {
            UUID secondSellerId = UUID.randomUUID();
            UUID secondEscrowId = UUID.randomUUID();
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    12_000L,
                    List.of(
                            new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 7_000L),
                            new OrderPaymentLineCommand(UUID.randomUUID(), secondSellerId, 5_000L)
                    ),
                    null
            );
            Wallet buyerWallet = Wallet.create(buyerWalletId, buyerMemberId, 20_000L, now, now);

            given(escrowRepository.findAllByOrderId(orderId)).willReturn(List.of());
            given(walletRepository.findByMemberId(buyerMemberId)).willReturn(java.util.Optional.of(buyerWallet));
            given(timeProvider.now()).willReturn(now);
            given(identifierGenerator.generateUuid())
                    .willReturn(UUID.randomUUID(), escrowId, secondEscrowId);
            given(walletRepository.save(any(Wallet.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(walletTransactionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(escrowRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

            OrderPaymentResult result = orderPaymentService.payOrder(command);

            assertThat(result.escrowIds()).containsExactly(escrowId, secondEscrowId);
            assertThat(result.buyerWalletBalance()).isEqualTo(8_000L);
            verify(walletTransactionRepository).save(any());
            verify(escrowRepository).saveAll(any());
        }

        @Test
        @DisplayName("이미 같은 orderId의 escrow가 있으면 기존 결과를 재사용한다")
        void payOrder_duplicateOrder_returnsExistingResult() {
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    12_000L,
                    List.of(new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 12_000L)),
                    null
            );
            Escrow existingEscrow = Escrow.createHeld(
                    escrowId,
                    orderId,
                    buyerMemberId,
                    sellerMemberId,
                    12_000L,
                    null,
                    now
            );
            Wallet buyerWallet = Wallet.create(buyerWalletId, buyerMemberId, 20_000L, now, now);

            given(escrowRepository.findAllByOrderId(orderId)).willReturn(List.of(existingEscrow));
            given(walletRepository.findByMemberId(buyerMemberId)).willReturn(java.util.Optional.of(buyerWallet));

            OrderPaymentResult result = orderPaymentService.payOrder(command);

            assertThat(result.escrowIds()).containsExactly(escrowId);
            assertThat(result.buyerWalletBalance()).isEqualTo(20_000L);
            verify(walletRepository, never()).save(any());
            verify(walletTransactionRepository, never()).save(any());
            verify(escrowRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("구매자 지갑이 없으면 WalletNotFoundException을 발생시킨다")
        void payOrder_walletNotFound_throwsException() {
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    12_000L,
                    List.of(new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 12_000L)),
                    null
            );

            given(escrowRepository.findAllByOrderId(orderId)).willReturn(List.of());
            given(walletRepository.findByMemberId(buyerMemberId)).willReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> orderPaymentService.payOrder(command))
                    .isInstanceOf(WalletNotFoundException.class);
        }

        @Test
        @DisplayName("seller 금액 합계가 주문 금액과 다르면 InvalidOrderPaymentRequestException을 발생시킨다")
        void payOrder_invalidSellerReceivable_throwsException() {
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    10_000L,
                    List.of(new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 12_000L)),
                    null
            );

            assertThatThrownBy(() -> orderPaymentService.payOrder(command))
                    .isInstanceOf(InvalidOrderPaymentRequestException.class)
                    .hasMessageContaining("lineAmount total must equal orderAmount");
        }

        @Test
        @DisplayName("금액이 부족하면 구매자 차감 없이 실패한다")
        void payOrder_insufficientBalance_throwsException() {
            OrderPaymentCommand command = new OrderPaymentCommand(
                    orderId,
                    buyerMemberId,
                    12_000L,
                    List.of(new OrderPaymentLineCommand(UUID.randomUUID(), sellerMemberId, 12_000L)),
                    null
            );
            Wallet buyerWallet = Wallet.create(buyerWalletId, buyerMemberId, 5_000L, now, now);

            given(escrowRepository.findAllByOrderId(orderId)).willReturn(List.of());
            given(walletRepository.findByMemberId(buyerMemberId)).willReturn(java.util.Optional.of(buyerWallet));
            given(timeProvider.now()).willReturn(now);

            assertThatThrownBy(() -> orderPaymentService.payOrder(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Balance is insufficient.");

            verify(walletRepository, never()).save(any());
            verify(walletTransactionRepository, never()).save(any());
            verify(escrowRepository, never()).saveAll(any());
        }
    }
}
