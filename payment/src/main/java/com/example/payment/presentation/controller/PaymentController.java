package com.example.payment.presentation.controller;

import com.todaylunch.common.security.auth.annotation.CurrentMember;
import com.todaylunch.common.security.auth.dto.AuthenticatedMember;
import com.example.payment.application.dto.CardPaymentConfirmCommand;
import com.example.payment.application.dto.ChargeConfirmCommand;
import com.example.payment.application.dto.ChargeConfirmFailureCommand;
import com.example.payment.application.dto.ChargeCreateCommand;
import com.example.payment.application.dto.ChargeRefundCommand;
import com.example.payment.application.dto.PaymentRefundCommand;
import com.example.payment.application.dto.PaymentRefundItemCommand;
import com.example.payment.application.usecase.CardPaymentConfirmUseCase;
import com.example.payment.application.usecase.ChargeConfirmFailureUseCase;
import com.example.payment.application.usecase.ChargeConfirmUseCase;
import com.example.payment.application.usecase.ChargeCreateUseCase;
import com.example.payment.application.usecase.ChargeRefundUseCase;
import com.example.payment.application.usecase.OrderPaymentApiUseCase;
import com.example.payment.application.usecase.PaymentRefundUseCase;
import com.example.payment.application.usecase.PaymentSearchUseCase;
import com.example.payment.presentation.dto.request.ChargeConfirmFailureRequest;
import com.example.payment.presentation.dto.request.ChargeConfirmRequest;
import com.example.payment.presentation.dto.request.ChargeCreateRequest;
import com.example.payment.presentation.dto.request.ChargeRefundRequest;
import com.example.payment.presentation.dto.request.CardPaymentConfirmRequest;
import com.example.payment.presentation.dto.request.OrderPaymentApiRequest;
import com.example.payment.presentation.dto.request.PaymentRefundRequest;
import com.example.payment.presentation.dto.response.ApiResponse;
import com.example.payment.presentation.dto.response.CardPaymentConfirmResponse;
import com.example.payment.presentation.dto.response.ChargeConfirmFailureResponse;
import com.example.payment.presentation.dto.response.ChargeDetailResponse;
import com.example.payment.presentation.dto.response.ChargeConfirmResponse;
import com.example.payment.presentation.dto.response.ChargeCreateResponse;
import com.example.payment.presentation.dto.response.ChargeListItemResponse;
import com.example.payment.presentation.dto.response.ChargeRefundSummaryResponse;
import com.example.payment.presentation.dto.response.ChargeRefundResponse;
import com.example.payment.presentation.dto.response.OrderPaymentApiResponse;
import com.example.payment.presentation.dto.response.PagedResponse;
import com.example.payment.presentation.dto.response.PaymentRefundResponse;
import com.example.payment.presentation.dto.response.PendingSellerIncomeItemResponse;
import com.example.payment.presentation.dto.response.WalletSummaryResponse;
import com.example.payment.presentation.dto.response.WalletTransactionItemResponse;
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

/**
 * payment 충전/조회 API 진입점이다.
 * 인증 정보는 {@code @CurrentMember}로 전달받고,
 * request DTO를 application command로 변환해 use case에 위임한다.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payment", description = "충전/지갑/환불 API")
public class PaymentController {

    private final ChargeCreateUseCase chargeCreateUseCase;
    private final ChargeConfirmUseCase chargeConfirmUseCase;
    private final CardPaymentConfirmUseCase cardPaymentConfirmUseCase;
    private final ChargeConfirmFailureUseCase chargeConfirmFailureUseCase;
    private final ChargeRefundUseCase chargeRefundUseCase;
    private final PaymentRefundUseCase paymentRefundUseCase;
    private final PaymentSearchUseCase paymentSearchUseCase;
    private final OrderPaymentApiUseCase orderPaymentApiUseCase;

    public PaymentController(
            ChargeCreateUseCase chargeCreateUseCase,
            ChargeConfirmUseCase chargeConfirmUseCase,
            CardPaymentConfirmUseCase cardPaymentConfirmUseCase,
            ChargeConfirmFailureUseCase chargeConfirmFailureUseCase,
            ChargeRefundUseCase chargeRefundUseCase,
            PaymentRefundUseCase paymentRefundUseCase,
            PaymentSearchUseCase paymentSearchUseCase,
            OrderPaymentApiUseCase orderPaymentApiUseCase
    ) {
        this.chargeCreateUseCase = chargeCreateUseCase;
        this.chargeConfirmUseCase = chargeConfirmUseCase;
        this.cardPaymentConfirmUseCase = cardPaymentConfirmUseCase;
        this.chargeConfirmFailureUseCase = chargeConfirmFailureUseCase;
        this.chargeRefundUseCase = chargeRefundUseCase;
        this.paymentRefundUseCase = paymentRefundUseCase;
        this.paymentSearchUseCase = paymentSearchUseCase;
        this.orderPaymentApiUseCase = orderPaymentApiUseCase;
    }

    /**
     * db에 반영된 현재 사용자의 wallet 값을 반환한다.
     */
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

    /**
     * 회원의 charge 목록을 최신순 페이지 응답으로 반환한다.
     */
    // todo: 프론트에서 페이지네이션을 처리할 것인지 페이지를 전달할 것인지 확인하기
    @GetMapping("/charges")
    @Operation(summary = "내 충전 목록 조회")
    public ResponseEntity<ApiResponse<PagedResponse<ChargeListItemResponse>>> findAllCharges(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // todo : var 대신에 PagedResult<ChargeListItemResult>을 쓸 것인지 결정하기, var를 쓴 이유를 확실히하기
        var result = paymentSearchUseCase.findAllCharges(authenticatedMember.memberId(), page, size);
        List<ChargeListItemResponse> items = result.items().stream()
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

    /**
     * chargeId로 충전 내역을 조회하고 refund에서 환불 여부를 확인해서 같이 내용을 전달한다.
     */
    // todo: 충전 상세 화면에 필요한 데이터가 최신 환불 이력 1건이 맞는지 확인해서 메서드 수정하기
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

    /**
     * 회원의 charge refund 목록을 최신순 페이지 응답으로 반환한다.
     */
    @GetMapping("/refunds")
    @Operation(summary = "내 환불 목록 조회")
    public ResponseEntity<ApiResponse<PagedResponse<ChargeRefundSummaryResponse>>> findAllRefunds(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // todo : var 대신에 PagedResult<ChargeListItemResult>을 쓸 것인지 결정하기, var를 쓴 이유를 확실히하기
        var result = paymentSearchUseCase.findAllRefunds(authenticatedMember.memberId(), page, size);
        List<ChargeRefundSummaryResponse> items = result.items().stream()
                .map(ChargeRefundSummaryResponse::from)
                .toList();
        PagedResponse<ChargeRefundSummaryResponse> response = new PagedResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 예치금 증감내역을 조회하는 API로, 충전/환불/주문결제 등 모든 거래내역이 포함된다.
     */
    @GetMapping("/transactions")
    @Operation(summary = "지갑 거래내역 조회")
    public ResponseEntity<ApiResponse<PagedResponse<WalletTransactionItemResponse>>> findAllTransactions(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // todo : var 대신에 PagedResult<ChargeListItemResult>을 쓸 것인지 결정하기, var를 쓴 이유를 확실히하기
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

    /**
     * 판매자 기준으로 아직 wallet에 반영되지 않은 HELD escrow를 반환한다.
     */
    @GetMapping("/seller/pending-incomes")
    @Operation(summary = "판매자 지급 대기 내역 조회")
    public ResponseEntity<ApiResponse<PagedResponse<PendingSellerIncomeItemResponse>>> findAllPendingSellerIncomes(
            @CurrentMember AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // todo : var 대신에 PagedResult<ChargeListItemResult>을 쓸 것인지 결정하기, var를 쓴 이유를 확실히하기
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

    /**
     * 충전 요청을 생성하고 PG 승인에 필요한 charge 식별 정보를 반환한다.
     */
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

    /**
     * 충전 실패 리다이렉트 결과를 db에 등록하고 결과를 반환한다.
     */
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
    /**
     * PG 승인 결과를 받아 charge와 wallet 상태를 확정한다.
     */
    @PostMapping("/confirm")
    @Operation(summary = "충전 승인 확정")
    public ResponseEntity<ApiResponse<ChargeConfirmResponse>> confirmCharge(@Valid @RequestBody ChargeConfirmRequest request) {
        ChargeConfirmCommand command = new ChargeConfirmCommand(
                request.chargeId(),
                request.paymentKey(),
                request.orderId(),
                request.amount()
        );
        ChargeConfirmResponse response = ChargeConfirmResponse.from(chargeConfirmUseCase.confirmCharge(command));
        // 공통 응답으로 감싸서 반환
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

    /**
     * 승인된 charge를 환불하고 wallet 잔액을 차감한다.
     */
    // todo: 경로에 chargeId를 넣는것이 적절한 것인가?
    // todo: front와 소통하여 chargeId를 body에 넣어서 보내는 방식으로 리팩토링 요청해야 할 수 있음
    @PostMapping("/charges/{chargeId}/refund")
    @Operation(summary = "충전 환불")
    public ResponseEntity<ApiResponse<ChargeRefundResponse>> refundCharge(
            @PathVariable UUID chargeId,
            @Valid @RequestBody ChargeRefundRequest request
            //todo: @CurrentMember를 받아 memberId를 이용 charge가 본인 데이터인지 검증 로직이 빠져있음.
    ) {
        ChargeRefundCommand command = new ChargeRefundCommand(chargeId, request.refundReason());
        ChargeRefundResponse response = ChargeRefundResponse.from(chargeRefundUseCase.refundCharge(command));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refunds")
    @Operation(summary = "주문 환불 요청")
    public ResponseEntity<ApiResponse<PaymentRefundResponse>> requestPaymentRefund(
            @Valid @RequestBody PaymentRefundRequest request
    ) {
        PaymentRefundCommand command = new PaymentRefundCommand(
                request.orderId(),
                request.buyerMemberId(),
                request.orderCancelRequestId(),
                request.refundType(),
                request.paymentMethod(),
                request.reason(),
                request.items().stream()
                        .map(item -> new PaymentRefundItemCommand(item.orderItemId(), item.refundAmount()))
                        .toList()
        );

        PaymentRefundResponse response = PaymentRefundResponse.from(paymentRefundUseCase.requestRefund(command));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * order에서 주문 생성이 완료되면 예치금 차감과 거래내역 및 에스크로 적재를 위한 api 통신
     */
    @PostMapping("/orders")
    @Operation(summary = "주문 결제")
    public ResponseEntity<ApiResponse<OrderPaymentApiResponse>> payOrder(
            @Valid @RequestBody OrderPaymentApiRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderPaymentApiUseCase.payOrder(request)));
    }
}
