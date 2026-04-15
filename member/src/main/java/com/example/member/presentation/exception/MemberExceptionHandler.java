package com.example.member.presentation.exception;

import com.example.member.common.exception.DuplicateActiveRestrictionException;
import com.example.member.common.exception.DuplicateMemberEmailException;
import com.example.member.common.exception.DuplicateMemberReportException;
import com.example.member.common.exception.EmailSendFailedException;
import com.example.member.common.exception.EmailVerificationNotAllowedException;
import com.example.member.common.exception.EmailVerificationRequiredException;
import com.example.member.common.exception.ExpiredEmailVerificationException;
import com.example.member.common.exception.InvalidLoginException;
import com.example.member.common.exception.InvalidEmailVerificationTokenException;
import com.example.member.common.exception.MemberNotFoundException;
import com.example.member.common.exception.MemberReportNotFoundException;
import com.example.member.common.exception.MemberRestrictedException;
import com.example.member.common.exception.MemberRestrictionNotFoundException;
import com.example.member.common.exception.PendingSellerRegistrationAlreadyExistsException;
import com.example.member.common.exception.PendingSellerRegistrationNotFoundException;
import com.example.member.common.exception.RefreshTokenNotFoundException;
import com.example.member.common.exception.SelfReportNotAllowedException;
import com.example.member.common.exception.SellerAlreadyRegisteredException;
import com.example.member.common.exception.SellerNotFoundException;
import com.example.member.presentation.dto.ApiResponse;
import com.todaylunch.common.security.exception.AuthorizationDeniedException;
import com.todaylunch.common.security.exception.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MemberExceptionHandler {

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberNotFound(MemberNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("MEMBER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateMemberEmailException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateEmail(DuplicateMemberEmailException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("DUPLICATE_MEMBER_EMAIL", exception.getMessage()));
    }

    @ExceptionHandler(SellerAlreadyRegisteredException.class)
    public ResponseEntity<ApiResponse<Object>> handleSellerAlreadyRegistered(
            SellerAlreadyRegisteredException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("SELLER_ALREADY_REGISTERED", exception.getMessage()));
    }

    @ExceptionHandler(SellerNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleSellerNotFound(SellerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("SELLER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(PendingSellerRegistrationAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handlePendingSellerRegistrationAlreadyExists(
            PendingSellerRegistrationAlreadyExistsException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("PENDING_SELLER_REGISTRATION_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(PendingSellerRegistrationNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handlePendingSellerRegistrationNotFound(
            PendingSellerRegistrationNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("PENDING_SELLER_REGISTRATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidLogin(InvalidLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("INVALID_LOGIN", exception.getMessage()));
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailVerificationRequired(
            EmailVerificationRequiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("EMAIL_VERIFICATION_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidEmailVerificationToken(
            InvalidEmailVerificationTokenException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("EMAIL_VERIFICATION_TOKEN_INVALID", exception.getMessage()));
    }

    @ExceptionHandler(ExpiredEmailVerificationException.class)
    public ResponseEntity<ApiResponse<Object>> handleExpiredEmailVerification(
            ExpiredEmailVerificationException exception
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.fail("EMAIL_VERIFICATION_TOKEN_EXPIRED", exception.getMessage()));
    }

    @ExceptionHandler(EmailVerificationNotAllowedException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailVerificationNotAllowed(
            EmailVerificationNotAllowedException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("EMAIL_VERIFICATION_NOT_ALLOWED", exception.getMessage()));
    }

    @ExceptionHandler(EmailSendFailedException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailSendFailed(EmailSendFailedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail("EMAIL_SEND_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(MemberRestrictedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberRestricted(MemberRestrictedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("MEMBER_RESTRICTED", exception.getMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthorizationDenied(AuthorizationDeniedException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateActiveRestrictionException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateActiveRestriction(
            DuplicateActiveRestrictionException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("DUPLICATE_ACTIVE_RESTRICTION", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateMemberReportException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateMemberReport(DuplicateMemberReportException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("DUPLICATE_MEMBER_REPORT", exception.getMessage()));
    }

    @ExceptionHandler(SelfReportNotAllowedException.class)
    public ResponseEntity<ApiResponse<Object>> handleSelfReportNotAllowed(SelfReportNotAllowedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("SELF_REPORT_NOT_ALLOWED", exception.getMessage()));
    }

    @ExceptionHandler(MemberReportNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberReportNotFound(MemberReportNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("MEMBER_REPORT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(MemberRestrictionNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleMemberRestrictionNotFound(
            MemberRestrictionNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("MEMBER_RESTRICTION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({InvalidTokenException.class, RefreshTokenNotFoundException.class})
    public ResponseEntity<ApiResponse<Object>> handleInvalidToken(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("INVALID_TOKEN", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("INVALID_STATE", exception.getMessage()));
    }
}
