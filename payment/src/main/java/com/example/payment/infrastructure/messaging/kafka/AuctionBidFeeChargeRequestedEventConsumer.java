package com.example.payment.infrastructure.messaging.kafka;

import com.example.payment.application.dto.AuctionDepositCommand;
import com.example.payment.application.dto.AuctionDepositResult;
import com.example.payment.application.usecase.AuctionDepositUseCase;
import com.example.payment.common.exception.AuctionBidFeeEventValidationException;
import com.example.payment.common.exception.CustomException;
import com.example.payment.common.exception.ErrorCode;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.TimeProvider;
import com.example.payment.infrastructure.messaging.kafka.contract.BidFeeChargeFailedMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.BidFeeChargeRequestMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.BidFeeChargeSucceededMessage;
import com.todaylunch.common.event.contract.EventEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * auction -> payment 경매 입찰 보증금 처리 요청 이벤트를 소비한다.
 */
@Slf4j
@Component
public class AuctionBidFeeChargeRequestedEventConsumer {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String BID_FEE_CHARGE_REQUESTED_EVENT_TYPE = "BID_FEE_CHARGE_REQUESTED";
    private static final String BID_FEE_CHARGE_SUCCEEDED_EVENT_TYPE = "BID_FEE_CHARGE_SUCCEEDED";
    private static final String BID_FEE_CHARGE_FAILED_EVENT_TYPE = "BID_FEE_CHARGE_FAILED";
    private static final TypeReference<EventEnvelope<BidFeeChargeRequestMessage>> BID_FEE_CHARGE_REQUESTED_ENVELOPE_TYPE =
            new TypeReference<>() {
            };

    private final AuctionDepositUseCase auctionDepositUseCase;
    private final AuctionBidFeeChargeResultOutboxEventSaver resultOutboxEventSaver;
    private final ObjectMapper objectMapper;
    private final IdentifierGenerator identifierGenerator;
    private final TimeProvider timeProvider;

    public AuctionBidFeeChargeRequestedEventConsumer(
            AuctionDepositUseCase auctionDepositUseCase,
            AuctionBidFeeChargeResultOutboxEventSaver resultOutboxEventSaver,
            ObjectMapper objectMapper,
            IdentifierGenerator identifierGenerator,
            TimeProvider timeProvider
    ) {
        this.auctionDepositUseCase = auctionDepositUseCase;
        this.resultOutboxEventSaver = resultOutboxEventSaver;
        this.objectMapper = objectMapper;
        this.identifierGenerator = identifierGenerator;
        this.timeProvider = timeProvider;
    }

    @KafkaListener(
            topics = KafkaTopics.AUCTION_BID_FEE_CHARGE_REQUESTED,
            groupId = KafkaConsumerGroups.PAYMENT_SERVICE,
            containerFactory = "auctionBidFeeChargeRequestedKafkaListenerContainerFactory"
    )
    public void listen(ConsumerRecord<String, String> record) {
        String eventJson = record.value();
        BidFeeChargeRequestMessage event = null;

        log.info("경매 입찰 보증금 처리 요청 이벤트 수신: topic={}, partition={}, offset={}, key={}, payloadSize={}, payloadSnippet={}",
                record.topic(), record.partition(), record.offset(), record.key(), payloadSize(eventJson),
                summarizePayload(eventJson));

        try {
            EventEnvelope<BidFeeChargeRequestMessage> envelope = readEnvelope(eventJson, record);
            validateEnvelope(envelope, record);
            event = envelope.payload();
            log.info("경매 입찰 보증금 처리 요청 envelope 검증 성공: eventId={}, eventType={}, aggregateId={}, recipientId={}",
                    envelope.eventId(), envelope.eventType(), envelope.aggregateId(), envelope.recipientId());

            validateEvent(event);
            log.info("경매 입찰 보증금 처리 요청 payload 검증 성공: bidId={}, auctionId={}, highestBidderId={}, highestBidderFee={}",
                    event.bidId(), event.auctionId(), event.highestBidderId(), event.highestBidderFee());

            AuctionDepositResult result = auctionDepositUseCase.processAuctionDeposit(toCommand(event));
            log.info("경매 입찰 보증금 처리 성공: bidId={}, auctionId={}", result.bidId(), result.auctionId());
            publishSuccess(result);
        } catch (AuctionBidFeeEventValidationException e) {
            log.error("경매 입찰 보증금 처리 요청 검증 실패: topic={}, partition={}, offset={}, key={}, payloadSnippet={}",
                    record.topic(), record.partition(), record.offset(), record.key(), summarizePayload(eventJson), e);
            throw e;
        } catch (CustomException e) {
            log.warn("경매 입찰 보증금 처리 비즈니스 실패 auctionId={} errorCode={}",
                    event == null ? null : event.auctionId(), e.getErrorCode().name(), e);
            if (!canPublishFailure(event)) {
                throw new RuntimeException("경매 입찰 보증금 실패 이벤트를 발행할 경매 ID가 없습니다.", e);
            }
            publishFailure(event, e.getErrorCode().name(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("경매 입찰 보증금 처리 실패: topic={}, partition={}, offset={}, key={}, auctionId={}, payloadSnippet={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    event == null ? null : event.auctionId(), summarizePayload(eventJson), e);
            throw e;
        }
    }

    private EventEnvelope<BidFeeChargeRequestMessage> readEnvelope(String eventJson, ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(eventJson, BID_FEE_CHARGE_REQUESTED_ENVELOPE_TYPE);
        } catch (Exception e) {
            log.error("경매 입찰 보증금 처리 요청 이벤트 역직렬화 실패: topic={}, partition={}, offset={}, key={}, payloadSnippet={}",
                    record.topic(), record.partition(), record.offset(), record.key(), summarizePayload(eventJson), e);
            throw new AuctionBidFeeEventValidationException(
                    ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED,
                    "경매 입찰 보증금 처리 요청 이벤트 역직렬화에 실패했습니다."
            );
        }
    }

    private void validateEnvelope(EventEnvelope<BidFeeChargeRequestMessage> envelope, ConsumerRecord<String, String> record) {
        if (envelope == null) {
            log.warn("경매 입찰 보증금 처리 요청 envelope 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (envelope.eventId() == null) {
            log.warn("경매 입찰 보증금 처리 요청 eventId 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (!BID_FEE_CHARGE_REQUESTED_EVENT_TYPE.equals(envelope.eventType())) {
            log.warn("경매 입찰 보증금 처리 요청 eventType 불일치: topic={}, partition={}, offset={}, actualEventType={}",
                    record.topic(), record.partition(), record.offset(), envelope.eventType());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (envelope.source() == null || envelope.source().isBlank()) {
            log.warn("경매 입찰 보증금 처리 요청 source 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (envelope.aggregateId() == null) {
            log.warn("경매 입찰 보증금 처리 요청 aggregateId 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_AUCTION_ID_REQUIRED);
        }
        if (envelope.occurredAt() == null) {
            log.warn("경매 입찰 보증금 처리 요청 occurredAt 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (envelope.traceId() == null || envelope.traceId().isBlank()) {
            log.warn("경매 입찰 보증금 처리 요청 traceId 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (envelope.payload() == null) {
            log.warn("경매 입찰 보증금 처리 요청 payload 누락: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (!Objects.equals(envelope.aggregateId(), envelope.payload().auctionId())) {
            log.warn("경매 입찰 보증금 처리 요청 aggregateId 불일치: topic={}, partition={}, offset={}, aggregateId={}, payloadAuctionId={}",
                    record.topic(), record.partition(), record.offset(), envelope.aggregateId(),
                    envelope.payload().auctionId());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_AUCTION_ID_REQUIRED);
        }
        if (envelope.recipientId() != null
                && !Objects.equals(envelope.recipientId(), envelope.payload().highestBidderId())) {
            log.warn("경매 입찰 보증금 처리 요청 recipientId 불일치: topic={}, partition={}, offset={}, recipientId={}, payloadHighestBidderId={}",
                    record.topic(), record.partition(), record.offset(), envelope.recipientId(),
                    envelope.payload().highestBidderId());
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_HIGHEST_BIDDER_REQUIRED);
        }
    }

    private void validateEvent(BidFeeChargeRequestMessage event) {
        if (event == null) {
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_EVENT_REQUIRED);
        }
        if (event.auctionId() == null) {
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_AUCTION_ID_REQUIRED);
        }
        if (event.highestBidderId() == null) {
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_HIGHEST_BIDDER_REQUIRED);
        }
        if (event.highestBidderFee() == null || event.highestBidderFee().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AuctionBidFeeEventValidationException(ErrorCode.AUCTION_BID_FEE_HIGHEST_FEE_INVALID);
        }
    }

    private AuctionDepositCommand toCommand(BidFeeChargeRequestMessage event) {
        return new AuctionDepositCommand(
                event.bidId(),
                event.auctionId(),
                event.highestBidderId(),
                event.highestBidderFee()
        );
    }

    private void publishSuccess(AuctionDepositResult result) {
        resultOutboxEventSaver.saveSuccess(BID_FEE_CHARGE_SUCCEEDED_EVENT_TYPE, new BidFeeChargeSucceededMessage(
                identifierGenerator.generateUuid(),
                result.bidId(),
                result.auctionId(),
                nowAsInstant()
        ));
    }

    private void publishFailure(BidFeeChargeRequestMessage event, String errorCode, String errorMessage) {
        resultOutboxEventSaver.saveFailure(BID_FEE_CHARGE_FAILED_EVENT_TYPE, new BidFeeChargeFailedMessage(
                identifierGenerator.generateUuid(),
                event.bidId(),
                event.auctionId(),
                errorCode,
                errorMessage,
                nowAsInstant()
        ));
    }

    private boolean canPublishFailure(BidFeeChargeRequestMessage event) {
        return event != null && event.auctionId() != null;
    }

    private int payloadSize(String payload) {
        return payload == null ? 0 : payload.length();
    }

    private String summarizePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "<empty>";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private Instant nowAsInstant() {
        return timeProvider.now().atZone(KOREA_ZONE_ID).toInstant();
    }
}
