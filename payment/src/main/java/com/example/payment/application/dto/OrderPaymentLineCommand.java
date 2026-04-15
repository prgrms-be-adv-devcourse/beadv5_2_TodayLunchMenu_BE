package com.example.payment.application.dto;

import java.util.UUID;

/**
 * 주문 결제 시 escrow를 생성할 orderItem 단위 입력이다.
 */
public record OrderPaymentLineCommand(
        UUID orderItemId,
        UUID sellerMemberId,
        Long lineAmount
) {
}
