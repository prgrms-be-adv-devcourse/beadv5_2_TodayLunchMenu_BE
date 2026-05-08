package com.example.payment.presentation.controller;

import com.todaylunch.common.security.auth.annotation.CurrentMember;
import com.todaylunch.common.security.auth.dto.AuthenticatedMember;
import com.todaylunch.common.security.auth.enumtype.MemberRole;
import com.example.payment.application.dto.CardPaymentConfirmCommand;
import com.example.payment.application.dto.ChargeConfirmCommand;
import com.example.payment.application.dto.ChargeConfirmFailureCommand;
import com.example.payment.application.dto.ChargeCreateCommand;
import com.example.payment.application.dto.AuctionDepositCommand;
import com.example.payment.application.dto.PaymentRefundCommand;
import com.example.payment.application.dto.PaymentRefundItemCommand;
import com.example.payment.application.dto.SellerRefundCommand;
import com.example.payment.application.dto.WithdrawCommand;
import com.example.payment.application.usecase.AuctionDepositUseCase;
import com.example.payment.application.usecase.CardPaymentConfirmUseCase;
import com.example.payment.application.usecase.ChargeConfirmFailureUseCase;
import com.example.payment.application.usecase.ChargeConfirmUseCase;
import com.example.payment.application.usecase.ChargeCreateUseCase;
import com.example.payment.application.usecase.OrderPaymentApiUseCase;
import com.example.payment.application.usecase.PaymentCancellationUseCase;
import com.example.payment.application.usecase.PaymentSearchUseCase;
import com.example.payment.application.usecase.SellerRefundUseCase;
import com.example.payment.application.usecase.WithdrawUseCase;
import com.example.payment.presentation.dto.request.CardPaymentConfirmRequest;
import com.example.payment.presentation.dto.request.ChargeConfirmFailureRequest;
import com.example.payment.presentation.dto.request.ChargeConfirmRequest;
import com.example.payment.presentation.dto.request.ChargeCreateRequest;
import com.example.payment.presentation.dto.request.AuctionFeeVerificationRequest;
import com.example.payment.presentation.dto.request.OrderPaymentApiRequest;
import com.example.payment.presentation.dto.request.PaymentCancellationRequest;
import com.example.payment.presentation.dto.request.SellerRefundConfirmRequest;
import com.example.payment.presentation.dto.request.WithdrawCreateRequest;
import com.example.payment.presentation.dto.response.ApiResponse;
import com.example.payment.presentation.dto.response.AuctionFeeVerificationResponse;
import com.example.payment.presentation.dto.response.CardPaymentConfirmResponse;
import com.example.payment.presentation.dto.response.ChargeConfirmFailureResponse;
import com.example.payment.presentation.dto.response.ChargeConfirmResponse;
import com.example.payment.presentation.dto.response.ChargeCreateResponse;
import com.example.payment.presentation.dto.response.ChargeDetailResponse;
import com.example.payment.presentation.dto.response.ChargeListItemResponse;
import com.example.payment.presentation.dto.response.EscrowTransactionItemResponse;
import com.example.payment.presentation.dto.response.OrderPaymentApiResponse;
import com.example.payment.presentation.dto.response.OrderPaymentDetailResponse;
import com.example.payment.presentation.dto.response.PagedResponse;
import com.example.payment.presentation.dto.response.PaymentRefundResponse;
import com.example.payment.presentation.dto.response.PendingSellerIncomeItemResponse;
import com.example.payment.presentation.dto.response.WalletSummaryResponse;
import com.example.payment.presentation.dto.response.WalletTransactionItemResponse;
import com.example.payment.presentation.dto.response.WithdrawListItemResponse;
import com.example.payment.presentation.dto.response.WithdrawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment", description = "충전/지갑/환불 API")
public class PaymentController {

    private final AuctionDepositUseCase auctionDepositUseCase;
    private final ChargeCreateUseCase chargeCreateUseCase;
    private final ChargeConfirmUseCase chargeConfirmUseCase;
    private final CardPaymentConfirmUseCase cardPaymentConfirmUseCase;
    private final ChargeConfirmFailureUseCase chargeConfirmFailureUseCase;
    private final PaymentCancellationUseCase paymentCancellationUseCase;
    private final SellerRefundUseCase sellerRefundUseCase;
    private final PaymentSearchUseCase paymentSearchUseCase;
    private final OrderPaymentApiUseCase orderPaymentApiUseCase;
    private final WithdrawUseCase withdrawUseCase;

    public PaymentController(
            AuctionDepositUseCase auctionDepositUseCase,
            ChargeCreateUseCase chargeCreateUseCase,
            ChargeConfirmUseCase chargeConfirmUseCase,
            CardPaymentConfirmUseCase cardPaymentConfirmUseCase,
            ChargeConfirmFailureUseCase chargeConfirmFailureUseCase,
            PaymentCancellationUseCase paymentCancellationUseCase,
            SellerRefundUseCase sellerRefundUseCase,
            PaymentSearchUseCase paymentSearchUseCase,
            OrderPaymentApiUseCase orderPaymentApiUseCase,
            WithdrawUseCase withdrawUseCase
    ) {
        this.auctionDepositUseCase = auctionDepositUseCase;
        this.chargeCreateUseCase = chargeCreateUseCase;
        this.chargeConfirmUseCase = chargeConfirmUseCase;
        this.cardPaymentConfirmUseCase = cardPaymentConfirmUseCase;
        this.chargeConfirmFailureUseCase = chargeConfirmFailureUseCase;
        this.paymentCancellationUseCase = paymentCancellationUseCase;
        this.sellerRefundUseCase = sellerRefundUseCase;
        this.paymentSearchUseCase = paymentSearchUseCase;
        this.orderPaymentApiUseCase = orderPaymentApiUseCase;
        this.withdrawUseCase = withdrawUseCase;
    }

    @PostMapping("/auctions/bid-fees")
    @Operation(summary = "경매 예치금 차감 및 환불 처리")
    public ResponseEntity<ApiResponse<AuctionFeeVerificationResponse>> verifyAuctionDeposit(
            @Valid @RequestBody AuctionFeeVerificationRequest request
    ) {
        AuctionFeeVerificationResponse response = AuctionFeeVerificationResponse.success(
                auctionDepositUseCase.processAuctionDeposit(new AuctionDepositCommand(
                        request.bidId(),
                        request.auctionId(),
                        request.highestBidderId(),
                        request.highestBidderFee()
                ))
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/wallet")
    @Operation(summary = "내 예치금 금액 조회")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> findWalletSummary(
            @CurrentMember AuthenticatedMember authenticatedMember
    ) {
        WalletSummaryResponse response = WalletSummaryResponse.from(
                paymentSearchUseCase.findWalletSummary(authenticatedMember.memberId())
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/charges")
    @Operation(summary = "내 충전 목록 조회")
    public ResponseEntity<ApiResponse<PagedResponse<ChargeListItemResponse>>> findAllCharges(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = paymentSearchUseCase.findAllCharges(authenticatedMember.memberId(), page, size);
        List<ChargeListItemResponse> items = result.items()
                                                   .stream()
                                                   .map(ChargeListItemResponse::from)
                                                   .toList();
        PagedResponse<ChargeListItemResponse> response = new PagedResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/charges/{chargeId}")
    @Operation(summary = "충전 상세 조회")
    public ResponseEntity<ApiResponse<ChargeDetailResponse>> findChargeDetail(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @PathVariable UUID chargeId
    ) {
        ChargeDetailResponse response = ChargeDetailResponse.from(
                paymentSearchUseCase.findChargeDetail(authenticatedMember.memberId(), chargeId)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    @Operation(summary = "지갑 거래내역 조회")
    public ResponseEntity<ApiResponse<PagedResponse<WalletTransactionItemResponse>>> findAllTransactions(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = paymentSearchUseCase.findAllTransactions(authenticatedMember.memberId(), page, size);
        List<WalletTransactionItemResponse> items = result.items().stream()
                                                          .map(WalletTransactionItemResponse::from)
                                                          .toList();
        PagedResponse<WalletTransactionItemResponse> response = new PagedResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/seller/pending-incomes")
    @Operation(summary = "판매자 지급 대기 내역 조회")
    public ResponseEntity<ApiResponse<PagedResponse<PendingSellerIncomeItemResponse>>> findAllPendingSellerIncomes(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = paymentSearchUseCase.findAllPendingSellerIncomes(authenticatedMember.memberId(), page, size);
        List<PendingSellerIncomeItemResponse> items = result.items().stream()
                                                            .map(PendingSellerIncomeItemResponse::from)
                                                            .toList();
        PagedResponse<PendingSellerIncomeItemResponse> response = new PagedResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "예치금 출금 내역 조회")
    public ResponseEntity<ApiResponse<PagedResponse<WithdrawListItemResponse>>> findAllWithdrawRequests(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = paymentSearchUseCase.findAllWithdrawRequests(authenticatedMember.memberId(), page, size);
        List<WithdrawListItemResponse> items = result.items().stream()
                                                     .map(WithdrawListItemResponse::from)
                                                     .toList();
        PagedResponse<WithdrawListItemResponse> response = new PagedResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/seller/orders/{orderId}/escrow-transactions")
    @Operation(summary = "판매자 주문 escrow transaction 이력 조회")
    public ResponseEntity<ApiResponse<List<EscrowTransactionItemResponse>>> findSellerOrderEscrowTransactions(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @PathVariable UUID orderId
    ) {
        if (authenticatedMember.role() != MemberRole.SELLER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "판매자 권한이 필요합니다.");
        }

        List<EscrowTransactionItemResponse> response = paymentSearchUseCase.findEscrowTransactionsByOrderId(
                                                                                   authenticatedMember.memberId(),
                                                                                   orderId
                                                                           ).stream()
                                                                           .map(EscrowTransactionItemResponse::from)
                                                                           .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/charge")
    @Operation(summary = "충전 요청 생성")
    public ResponseEntity<ApiResponse<ChargeCreateResponse>> createCharge(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ChargeCreateRequest request
    ) {
        ChargeCreateCommand command = new ChargeCreateCommand(
                authenticatedMember.memberId(),
                request.amount()
        );
        // response 형 변환이 컨트롤러에서 이루어지는 것은 아쉽지만 클린아키텍처상 application 결과를 외부 응답 형식으로
        // 바꾸는 것은 외부 계층의 책임이는 ai의 판단이 적절하다고 생각함.
        ChargeCreateResponse response = ChargeCreateResponse.from(chargeCreateUseCase.createCharge(command));
        // 코딩 컨벤션에서 정한 공통 응답으로 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/charge/fail")
    @Operation(summary = "충전 실패 반영")
    public ResponseEntity<ApiResponse<ChargeConfirmFailureResponse>> confirmChargeFailure(
            @Valid @RequestBody ChargeConfirmFailureRequest request
    ) {
        ChargeConfirmFailureCommand command = new ChargeConfirmFailureCommand(
                request.orderId(),
                request.code(),
                request.message()
        );
        ChargeConfirmFailureResponse response = ChargeConfirmFailureResponse.from(
                chargeConfirmFailureUseCase.confirmChargeFailure(command)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm")
    @Operation(summary = "충전 승인 확정")
    public ResponseEntity<ApiResponse<ChargeConfirmResponse>> confirmCharge(
            @Valid @RequestBody ChargeConfirmRequest request
    ) {
        ChargeConfirmCommand command = new ChargeConfirmCommand(
                request.chargeId(),
                request.paymentKey(),
                request.orderId(),
                request.amount()
        );
        ChargeConfirmResponse response = ChargeConfirmResponse.from(chargeConfirmUseCase.confirmCharge(command));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/card/confirm")
    @Operation(summary = "카드 결제 승인 확정")
    public ResponseEntity<ApiResponse<CardPaymentConfirmResponse>> confirmCardPayment(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @Valid @RequestBody CardPaymentConfirmRequest request
    ) {
        CardPaymentConfirmCommand command = new CardPaymentConfirmCommand(
                authenticatedMember.memberId(),
                request.orderId(),
                request.paymentKey(),
                request.amount()
        );
        CardPaymentConfirmResponse response = CardPaymentConfirmResponse.from(
                cardPaymentConfirmUseCase.confirmCardPayment(command)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/cancellations")
    @Operation(summary = "주문 취소 요청")
    public ResponseEntity<ApiResponse<PaymentRefundResponse>> requestOrderCancellation(
            @Valid @RequestBody PaymentCancellationRequest request
    ) {
        PaymentRefundCommand command = new PaymentRefundCommand(
                request.orderId(),
                request.buyerMemberId(),
                request.orderCancelRequestId(),
                request.refundType(),
                request.reason(),
                request.items().stream()
                       .map(item -> new PaymentRefundItemCommand(item.orderItemId(), item.refundAmount()))
                       .toList()
        );
        PaymentRefundResponse response = PaymentRefundResponse.from(
                paymentCancellationUseCase.requestCancellation(command));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/seller/refunds/confirm")
    @Operation(summary = "판매자 반품 수령 확인 후 환불")
    public ResponseEntity<ApiResponse<PaymentRefundResponse>> requestSellerRefundConfirm(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @Valid @RequestBody SellerRefundConfirmRequest request
    ) {
        if (authenticatedMember.role() != MemberRole.SELLER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "판매자 권한이 필요합니다.");
        }

        SellerRefundCommand command = new SellerRefundCommand(
                request.orderId(),
                authenticatedMember.memberId(),
                request.orderCancelRequestId(),
                request.refundType(),
                request.reason(),
                request.items().stream()
                       .map(item -> item.orderItemId())
                       .toList()
        );
        PaymentRefundResponse response = PaymentRefundResponse.from(sellerRefundUseCase.requestSellerRefund(command));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "주문 결제 정보 조회")
    public ResponseEntity<ApiResponse<OrderPaymentDetailResponse>> findOrderPayment(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @PathVariable UUID orderId
    ) {
        OrderPaymentDetailResponse response = OrderPaymentDetailResponse.from(
                paymentSearchUseCase.findOrderPaymentByOrderId(authenticatedMember.memberId(), orderId)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/orders")
    @Operation(summary = "주문 결제")
    public ResponseEntity<ApiResponse<OrderPaymentApiResponse>> payOrder(
            @Valid @RequestBody OrderPaymentApiRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderPaymentApiUseCase.payOrder(request)));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "예치금 출금 요청")
    public ResponseEntity<ApiResponse<WithdrawResponse>> withdraw(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @Valid @RequestBody WithdrawCreateRequest request
    ) {
        WithdrawResponse response = WithdrawResponse.from(withdrawUseCase.withdraw(
                new WithdrawCommand(
                        authenticatedMember.memberId(),
                        request.amount(),
                        request.bankAccount(),
                        request.accountHolder()
                )
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
