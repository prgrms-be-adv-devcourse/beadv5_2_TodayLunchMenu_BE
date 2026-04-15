package com.example.payment.application.usecase;

import com.example.payment.application.dto.PaymentRefundCommand;
import com.example.payment.application.dto.PaymentRefundResult;

public interface PaymentRefundUseCase {

    PaymentRefundResult requestRefund(PaymentRefundCommand command);
}
