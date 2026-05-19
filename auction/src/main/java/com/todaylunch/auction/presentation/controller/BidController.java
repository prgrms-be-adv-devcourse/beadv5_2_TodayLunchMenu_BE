package com.todaylunch.auction.presentation.controller;

import com.todaylunch.auction.application.usecase.BidCreateUseCase;
import com.todaylunch.auction.application.usecase.BidSearchUseCase;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.presentation.dto.request.BidPlaceRequest;
import com.todaylunch.auction.presentation.dto.response.ApiResponse;
import com.todaylunch.auction.presentation.dto.response.BidResponse;
import com.todaylunch.auction.presentation.dto.response.PagedResponse;
import com.todaylunch.common.security.auth.annotation.CurrentMember;
import com.todaylunch.common.security.auth.dto.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidCreateUseCase bidCreateUseCase;
    private final BidSearchUseCase bidSearchUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<BidResponse>> place(
            @PathVariable UUID auctionId,
            @CurrentMember AuthenticatedMember member,
            @Valid @RequestBody BidPlaceRequest request
    ) {
        BidResponse response = bidCreateUseCase.place(auctionId, member.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<BidResponse>>> search(
            @PathVariable UUID auctionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<BidResponse> response = bidSearchUseCase.searchByAuction(auctionId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<BidStatus, Long>>> stats(@PathVariable UUID auctionId) {
        return ResponseEntity.ok(ApiResponse.success(bidSearchUseCase.statsBy(auctionId)));
    }
}
