package com.todaylunch.auction.infrastructure.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.todaylunch.auction.application.port.BidFeeChargePort;
import com.todaylunch.auction.application.port.dto.request.BidFeeChargeRequest;
import com.todaylunch.auction.application.port.dto.response.BidFeeChargeResponse;
import com.todaylunch.auction.common.exception.CustomException;
import com.todaylunch.auction.common.exception.application.BidFeeChargeFailedException;
import com.todaylunch.auction.common.exception.application.BidFeeChargeRequestInvalidException;
import com.todaylunch.auction.common.exception.application.BidderWalletNotFoundException;
import com.todaylunch.auction.common.exception.application.DepositStateConflictException;
import com.todaylunch.auction.common.exception.application.InsufficientDepositException;
import com.todaylunch.auction.common.exception.application.PreviousDepositNotFoundException;
import com.todaylunch.auction.infrastructure.client.dto.request.ClientBidFeeChargeRequest;
import com.todaylunch.auction.infrastructure.client.dto.response.ClientBidFeeChargeResponse;
import com.todaylunch.auction.presentation.dto.response.ApiErrorResponse;
import com.todaylunch.auction.presentation.dto.response.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentClientAdapter implements BidFeeChargePort {

    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    @Override
    public BidFeeChargeResponse chargeBidFee(BidFeeChargeRequest request) {
        ClientBidFeeChargeRequest externalRequest = toExternalRequest(request);
        ApiResponse<ClientBidFeeChargeResponse> apiResponse;
        try {
            apiResponse = paymentClient.chargeBidFee(externalRequest);
        } catch (FeignException e) {
            throw translateFeignException(e);
        }

        if (!apiResponse.success()) {
            throw translateErrorCode(apiResponse.error());
        }
        return toResponse(apiResponse.data());
    }

    private CustomException translateFeignException(FeignException e) {
        ApiErrorResponse error = parseErrorBody(e);
        if (error != null && error.code() != null) {
            return translateErrorCode(error);
        }
        log.warn("payment 에러 응답 파싱 실패: status={}, body={}", e.status(), e.contentUTF8());
        return new BidFeeChargeFailedException();
    }

    private ApiErrorResponse parseErrorBody(FeignException e) {
        try {
            ApiResponse<Object> body = objectMapper.readValue(
                    e.contentUTF8(),
                    new TypeReference<>() {}
            );
            return body.error();
        } catch (Exception ignored) {
            return null;
        }
    }

    private CustomException translateErrorCode(ApiErrorResponse error) {
        log.warn("payment 수수료 차감 실패: code={}, message={}", error.code(), error.message());
        return switch (error.code()) {
            case "INSUFFICIENT_WALLET_BALANCE" -> new InsufficientDepositException();
            case "WALLET_NOT_FOUND" -> new BidderWalletNotFoundException();
            case "AUCTION_DEPOSIT_NOT_FOUND" -> new PreviousDepositNotFoundException();
            case "INVALID_STATE" -> new DepositStateConflictException();
            case "INVALID_INPUT_VALUE", "INVALID_AUCTION_BID_FEE_REQUEST"
                    -> new BidFeeChargeRequestInvalidException();
            default -> new BidFeeChargeFailedException();
        };
    }

    private ClientBidFeeChargeRequest toExternalRequest(BidFeeChargeRequest request) {
        return new ClientBidFeeChargeRequest(
                request.bidId(),
                request.auctionId(),
                request.highestBidderId(),
                request.highestBidderFee()
        );
    }

    private BidFeeChargeResponse toResponse(ClientBidFeeChargeResponse response) {
        return new BidFeeChargeResponse(
                response.auctionId(),
                response.highestBidderId(),
                response.heldAmount(),
                response.previousBidderId(),
                response.refundedAmount()
        );
    }
}
