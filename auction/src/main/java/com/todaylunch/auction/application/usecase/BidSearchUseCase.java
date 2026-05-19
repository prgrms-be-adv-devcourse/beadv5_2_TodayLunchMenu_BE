package com.todaylunch.auction.application.usecase;

import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.presentation.dto.response.BidResponse;
import com.todaylunch.auction.presentation.dto.response.PagedResponse;
import java.util.Map;
import java.util.UUID;

public interface BidSearchUseCase {

    PagedResponse<BidResponse> searchByAuction(UUID auctionId, int page, int size);

    Map<BidStatus, Long> statsBy(UUID auctionId);
}
