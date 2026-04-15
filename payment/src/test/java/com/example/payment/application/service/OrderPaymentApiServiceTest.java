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
import com.example.payment.application.usecase.OrderPaymentUseCase;
import com.example.payment.common.exception.InvalidOrderPaymentRequestException;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.domain.service.OrderPaymentResultEventPublisher;
import com.example.payment.infrastructure.messaging.kafka.contract.OrderPaymentFailureReason;
import com.example.payment.infrastructure.messaging.kafka.contract.OrderPaymentResultMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.OrderPaymentResultStatus;
import com.example.payment.presentation.dto.request.OrderPaymentApiOrderLineRequest;
import com.example.payment.presentation.dto.request.OrderPaymentApiRequest;
import com.example.payment.presentation.dto.response.OrderPaymentApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentApiService 테스트")
class OrderPaymentApiServiceTest {

    @Mock
    private OrderPaymentUseCase orderPaymentUseCase;

    @Mock
    private OrderPaymentResultEventPublisher orderPaymentResultEventPublisher;

    @InjectMocks
    private OrderPaymentApiService orderPaymentApiService;

    @Test
    @DisplayName("정상 요청이면 기존 결제 유스케이스를 호출하고 성공 응답과 결과 이벤트를 반환한다")
    void payOrder_success_returnsSuccessResponseAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        OrderPaymentApiRequest request = new OrderPaymentApiRequest(
                orderId,
                buyerId,
                BigDecimal.valueOf(12000L),
                Instant.parse("2026-04-01T10:00:00Z"),
                List.of(new OrderPaymentApiOrderLineRequest(
                        UUID.randomUUID(),
                        sellerId,
                        BigDecimal.valueOf(12000L),
                        1,
                        BigDecimal.valueOf(12000L)
                ))
        );
        given(orderPaymentUseCase.payOrder(any(OrderPaymentCommand.class)))
                .willReturn(new OrderPaymentResult(
                        orderId,
                        UUID.randomUUID(),
                        List.of(UUID.randomUUID()),
                        12000L,
                        3000L
                ));

        OrderPaymentApiResponse response = orderPaymentApiService.payOrder(request);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.buyerMemberId()).isEqualTo(buyerId);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(12000L));
        assertThat(response.status()).isEqualTo(OrderPaymentResultStatus.SUCCESS.name());
        assertThat(response.reasonCode()).isNull();

        ArgumentCaptor<OrderPaymentCommand> commandCaptor = ArgumentCaptor.forClass(OrderPaymentCommand.class);
        verify(orderPaymentUseCase).payOrder(commandCaptor.capture());
        assertThat(commandCaptor.getValue().paymentLines())
                .containsExactly(new OrderPaymentLineCommand(
                        request.orderLines().get(0).orderItemId(),
                        sellerId,
                        12000L
                ));

        ArgumentCaptor<OrderPaymentResultMessage> eventCaptor = ArgumentCaptor.forClass(OrderPaymentResultMessage.class);
        verify(orderPaymentResultEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(eventCaptor.getValue().buyerMemberId()).isEqualTo(buyerId);
        assertThat(eventCaptor.getValue().status()).isEqualTo(OrderPaymentResultStatus.SUCCESS);
        assertThat(eventCaptor.getValue().reasonCode()).isNull();
    }

    @Test
    @DisplayName("잔액 부족이면 실패 응답과 INSUFFICIENT_BALANCE 결과 이벤트를 반환한다")
    void payOrder_insufficientBalance_returnsFailedResponseAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        OrderPaymentApiRequest request = validRequest(orderId, buyerId, BigDecimal.valueOf(12000L));
        given(orderPaymentUseCase.payOrder(any(OrderPaymentCommand.class)))
                .willThrow(new IllegalArgumentException("Balance is insufficient."));

        OrderPaymentApiResponse response = orderPaymentApiService.payOrder(request);

        assertThat(response.status()).isEqualTo(OrderPaymentResultStatus.FAILED.name());
        assertThat(response.reasonCode()).isEqualTo(OrderPaymentFailureReason.INSUFFICIENT_BALANCE.name());

        ArgumentCaptor<OrderPaymentResultMessage> eventCaptor = ArgumentCaptor.forClass(OrderPaymentResultMessage.class);
        verify(orderPaymentResultEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(OrderPaymentResultStatus.FAILED);
        assertThat(eventCaptor.getValue().reasonCode()).isEqualTo(OrderPaymentFailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("지갑이 없으면 실패 응답과 WALLET_NOT_FOUND 결과 이벤트를 반환한다")
    void payOrder_walletNotFound_returnsFailedResponseAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        OrderPaymentApiRequest request = validRequest(orderId, buyerId, BigDecimal.valueOf(12000L));
        given(orderPaymentUseCase.payOrder(any(OrderPaymentCommand.class)))
                .willThrow(new WalletNotFoundException());

        OrderPaymentApiResponse response = orderPaymentApiService.payOrder(request);

        assertThat(response.status()).isEqualTo(OrderPaymentResultStatus.FAILED.name());
        assertThat(response.reasonCode()).isEqualTo(OrderPaymentFailureReason.WALLET_NOT_FOUND.name());

        ArgumentCaptor<OrderPaymentResultMessage> eventCaptor = ArgumentCaptor.forClass(OrderPaymentResultMessage.class);
        verify(orderPaymentResultEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reasonCode()).isEqualTo(OrderPaymentFailureReason.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("합계가 totalPrice와 다르면 유스케이스 호출 전에 검증 예외가 발생한다")
    void payOrder_totalMismatch_throwsBeforeUseCaseCall() {
        OrderPaymentApiRequest request = new OrderPaymentApiRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(12000L),
                Instant.parse("2026-04-01T10:00:00Z"),
                List.of(new OrderPaymentApiOrderLineRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BigDecimal.valueOf(5000L),
                        1,
                        BigDecimal.valueOf(5000L)
                ))
        );

        assertThatThrownBy(() -> orderPaymentApiService.payOrder(request))
                .isInstanceOf(InvalidOrderPaymentRequestException.class)
                .hasMessageContaining("lineTotalPrice total must equal totalPrice");

        verify(orderPaymentUseCase, never()).payOrder(any(OrderPaymentCommand.class));
        verify(orderPaymentResultEventPublisher, never()).publish(any(OrderPaymentResultMessage.class));
    }

    @Test
    @DisplayName("소수 금액이면 실패 응답과 INVALID_REQUEST 결과 이벤트를 반환한다")
    void payOrder_fractionalPrice_returnsFailedResponse() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        OrderPaymentApiRequest request = new OrderPaymentApiRequest(
                orderId,
                buyerId,
                BigDecimal.valueOf(10000.5),
                Instant.parse("2026-04-01T10:00:00Z"),
                List.of(new OrderPaymentApiOrderLineRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BigDecimal.valueOf(10000.5),
                        1,
                        BigDecimal.valueOf(10000.5)
                ))
        );

        OrderPaymentApiResponse response = orderPaymentApiService.payOrder(request);

        assertThat(response.status()).isEqualTo(OrderPaymentResultStatus.FAILED.name());
        assertThat(response.reasonCode()).isEqualTo(OrderPaymentFailureReason.INVALID_REQUEST.name());
        verify(orderPaymentUseCase, never()).payOrder(any(OrderPaymentCommand.class));
        verify(orderPaymentResultEventPublisher).publish(any(OrderPaymentResultMessage.class));
    }

    private OrderPaymentApiRequest validRequest(UUID orderId, UUID buyerId, BigDecimal totalPrice) {
        return new OrderPaymentApiRequest(
                orderId,
                buyerId,
                totalPrice,
                Instant.parse("2026-04-01T10:00:00Z"),
                List.of(new OrderPaymentApiOrderLineRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        totalPrice,
                        1,
                        totalPrice
                ))
        );
    }
}
