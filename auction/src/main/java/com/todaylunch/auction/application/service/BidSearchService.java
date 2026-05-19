package com.todaylunch.auction.application.service;

import com.todaylunch.auction.application.usecase.BidSearchUseCase;
import com.todaylunch.auction.domain.entity.Bid;
import com.todaylunch.auction.domain.enumtype.BidStatus;
import com.todaylunch.auction.domain.repository.BidRepository;
import com.todaylunch.auction.presentation.dto.response.BidResponse;
import com.todaylunch.auction.presentation.dto.response.PagedResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidSearchService implements BidSearchUseCase {

    private static final int MAX_PAGE_SIZE = 30;

    private final BidRepository bidRepository;

    @Override
    public PagedResponse<BidResponse> searchByAuction(UUID auctionId, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        PageRequest pageRequest = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "bidPrice").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Page<Bid> result = bidRepository.findAllByAuctionId(auctionId, pageRequest);

        List<BidResponse> items = result.getContent().stream()
                .map(BidResponse::from)
                .toList();

        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Override
    public Map<BidStatus, Long> statsBy(UUID auctionId) {
        return bidRepository.countByStatusForAuction(auctionId);
    }
}
