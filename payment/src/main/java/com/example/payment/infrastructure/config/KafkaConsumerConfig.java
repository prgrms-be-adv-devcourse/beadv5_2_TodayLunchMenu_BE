package com.example.payment.infrastructure.config;

import com.example.payment.common.exception.AuctionBidFeeEventValidationException;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.infrastructure.messaging.kafka.KafkaConsumerGroups;
import com.example.payment.infrastructure.messaging.kafka.KafkaTopics;
import com.todaylunch.common.messaging.kafka.DlqErrorHandlerFactory;
import com.todaylunch.common.messaging.kafka.KafkaConsumerProps;
import com.example.payment.infrastructure.messaging.kafka.contract.MemberCreatedMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.OrderPurchaseConfirmedMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * payment 모듈 Kafka consumer(소비기) 설정을 담당한다.
 * <p>
 * 역할:
 * 1. 이벤트 타입별 ConsumerFactory를 만든다.
 * 2. @KafkaListener가 사용할 ListenerContainerFactory를 만든다.
 * 3. 특정 이벤트에 대해 재시도, 백오프, DLQ 같은 실패 처리 정책을 설정한다.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * 회원 생성 이벤트를 소비하기 위한 ConsumerFactory
     * <p>
     * 여기서 bootstrap server, consumer group, deserializer 같은
     * 공통 소비 설정이 들어간다.
     */
    @Bean
    public ConsumerFactory<String, MemberCreatedMessage> memberCreatedConsumerFactory(
            // Kafka broker 주소
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return createConsumerFactory(bootstrapServers, KafkaConsumerGroups.PAYMENT_SERVICE, MemberCreatedMessage.class);
    }

    /**
     * 회원 생성 이벤트를 처리할 KafkaListenerContainerFactory
     * <p>
     * 실제 @KafkaListener에서 containerFactory 이름으로 참조해서 사용한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MemberCreatedMessage>
        memberCreatedKafkaListenerContainerFactory(
            ConsumerFactory<String, MemberCreatedMessage> memberCreatedConsumerFactory
    ) {
        // ConcurrentKafkaListenerContainerFactory는 @KafkaListener 실행 환경을 만드는 공장
        ConcurrentKafkaListenerContainerFactory<String, MemberCreatedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // 어떤 ConsumerFactory를 사용해서 Consumer를 만들지 지정
        factory.setConsumerFactory(memberCreatedConsumerFactory);
        return factory;
    }

    /**
     * 주문 구매 확정 이벤트용 ConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, OrderPurchaseConfirmedMessage> orderPurchaseConfirmedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return createConsumerFactory(bootstrapServers, KafkaConsumerGroups.PAYMENT_SERVICE, OrderPurchaseConfirmedMessage.class);
    }

    /**
     * 배송 완료 이벤트용 ConsumerFactory
     */

    /**
     * 배송 완료 이벤트를 처리할 ListenerContainerFactory
     */
    /**
     * 주문 구매 확정 이벤트를 처리할 ListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderPurchaseConfirmedMessage>
        orderPurchaseConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, OrderPurchaseConfirmedMessage> orderPurchaseConfirmedConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderPurchaseConfirmedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderPurchaseConfirmedConsumerFactory);
        return factory;
    }

    /**
     * 경매 입찰 보증금 처리 요청 이벤트용 ConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, String> auctionBidFeeChargeRequestedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return createConsumerFactory(bootstrapServers, KafkaConsumerGroups.PAYMENT_SERVICE, String.class);
    }

    /**
     * 경매 입찰 보증금 처리 요청 이벤트를 처리할 ListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        auctionBidFeeChargeRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, String> auctionBidFeeChargeRequestedConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.consumer.auction-bid-fee.concurrency:8}") int concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionBidFeeChargeRequestedConsumerFactory);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(DlqErrorHandlerFactory.create(
                kafkaTemplate,
                KafkaTopics.AUCTION_BID_FEE_CHARGE_REQUESTED_DLQ,
                AuctionBidFeeEventValidationException.class));
        return factory;
    }

    /**
     * 경매 입찰 예치금 환불 요청 이벤트용 ConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, String> auctionBidFeeRefundRequestedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return createConsumerFactory(bootstrapServers, KafkaConsumerGroups.PAYMENT_SERVICE, String.class);
    }

    /**
     * 경매 입찰 예치금 환불 요청 이벤트를 처리할 ListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        auctionBidFeeRefundRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, String> auctionBidFeeRefundRequestedConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionBidFeeRefundRequestedConsumerFactory);
        factory.setCommonErrorHandler(DlqErrorHandlerFactory.create(
                kafkaTemplate,
                KafkaTopics.AUCTION_BID_FEE_REFUND_REQUESTED_DLQ,
                AuctionBidFeeEventValidationException.class));
        return factory;
    }

    /**
     * 판매자 정산 지급 요청 이벤트용 ConsumerFactory
     * <p>
     * 이 이벤트는 다른 이벤트보다 실패 처리 정책이 중요하므로
     * 별도 ListenerContainerFactory에서 에러 핸들러까지 연결한다.
     */
    @Bean
    public ConsumerFactory<String, String> sellerSettlementPayoutRequestedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return createConsumerFactory(bootstrapServers, KafkaConsumerGroups.PAYMENT_SERVICE, String.class);
    }

    /**
     * 판매자 정산 지급 요청 이벤트 전용 ListenerContainerFactory
     *
     * 일반 이벤트와 다른 점
     * - ConsumerFactory를 연결할 뿐 아니라
     * - 공통 에러 핸들러(CommonErrorHandler)를 연결한다.
     *
     * 이 에러 핸들러는
     * - 재시도
     * - 재시도 간 대기 시간 증가(백오프)
     * - 최종 실패 시 DLQ 발행
     * 을 담당한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        sellerSettlementPayoutRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, String> sellerSettlementPayoutRequestedConsumerFactory,
            // DLQ로 메시지를 다시 발행할 때 사용할 KafkaTemplate
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // 이 Listener가 사용할 ConsumerFactory 설정
        factory.setConsumerFactory(sellerSettlementPayoutRequestedConsumerFactory);
        // 공통 에러 핸들러 연결 (재시도 + DLQ 패턴 — common-messaging의 Factory 재사용)
        factory.setCommonErrorHandler(DlqErrorHandlerFactory.create(
                kafkaTemplate,
                KafkaTopics.SETTLEMENT_PAYOUT_REQUESTED_DLQ,
                IllegalArgumentException.class,
                WalletNotFoundException.class));
        return factory;
    }

    /**
     * 공통 ConsumerFactory 생성 메서드 (common-messaging의 KafkaConsumerProps 위임).
     */
    private <T> ConsumerFactory<String, T> createConsumerFactory(
            String bootstrapServers,
            String groupId,
            Class<T> targetType
    ) {
        return new DefaultKafkaConsumerFactory<>(
                KafkaConsumerProps.defaults(bootstrapServers, groupId)
        );
    }

    private String summarizePayload(ConsumerRecord<?, ?> record) {
        Object value = record.value();
        if (value == null) {
            return "<empty>";
        }
        String normalized = value.toString().replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }
}
