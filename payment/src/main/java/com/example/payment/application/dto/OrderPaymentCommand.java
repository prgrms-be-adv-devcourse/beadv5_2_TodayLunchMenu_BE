package com.example.payment.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * payment 내부 주문 결제 유스케이스 입력 command다.
 * 외부 order 이벤트를 seller별 정산 단위로 정규화한 결과를 담는다.
 */
public record OrderPaymentCommand(
        UUID orderId,
        UUID buyerMemberId,
        Long orderAmount,
        List<OrderPaymentLineCommand> paymentLines,
        LocalDateTime releaseAt
) {
}
