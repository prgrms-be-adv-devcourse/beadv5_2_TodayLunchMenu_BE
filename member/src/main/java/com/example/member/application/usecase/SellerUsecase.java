package com.example.member.application.usecase;

import com.example.member.presentation.dto.SellerPendingResponse;
import com.example.member.presentation.dto.SellerRegisterRequest;
import com.example.member.presentation.dto.SellerRegisterResponse;
import com.example.member.presentation.dto.SellerResponse;
import java.util.UUID;

public interface SellerUsecase {

    SellerRegisterResponse registerSeller(UUID memberId, SellerRegisterRequest request);

    SellerPendingResponse getPendingSellerRegistration(UUID memberId);

    SellerResponse getCurrentSeller(UUID memberId);
}
