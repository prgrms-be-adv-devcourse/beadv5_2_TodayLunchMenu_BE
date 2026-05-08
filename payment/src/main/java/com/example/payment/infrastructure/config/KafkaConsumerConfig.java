package com.example.payment.infrastructure.config;

import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.infrastructure.messaging.kafka.KafkaConsumerGroups;
import com.example.payment.infrastructure.messaging.kafka.KafkaRetryPolicy;
import com.example.payment.infrastructure.messaging.kafka.KafkaTopics;
import com.example.payment.infrastructure.messaging.kafka.contract.MemberCreatedMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.OrderPurchaseConfirmedMessage;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;
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
            @Value("${kafka.consumer.auction-bid-fee.concurrency:8}") int concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auctionBidFeeChargeRequestedConsumerFactory);
        factory.setConcurrency(concurrency);
        factory.setCommonErrorHandler(createAuctionBidFeeChargeRequestedErrorHandler());
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
        // 공통 에러 핸들러 연결
        // listener 처리 중 예외가 발생하면 이 정책에 따라 재시도 / DLQ 수행
        factory.setCommonErrorHandler(createPayoutRequestedErrorHandler(
                kafkaTemplate
        ));
        return factory;
    }

    /**
     * 공통 ConsumerFactory 생성 메서드
     * <p>
     * 제네릭으로 타입만 바꿔 재사용하려는 의도다.
     * 현재는 targetType을 인자로 받지만 내부에서 실제로 사용하지는 않는다.
     */
    private <T> ConsumerFactory<String, T> createConsumerFactory(
            String bootstrapServers,
            String groupId,
            Class<T> targetType
    ) {
        Map<String, Object> props = new HashMap<>();
        // Kafka 서버 주소
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // consumer group 이름
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 오프셋이 없을 때 가장 처음 메시지부터 읽음
        // 운영 환경에서는 신중하게 선택해야 하는 옵션.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // key는 문자열로 역직렬화
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // value도 문자열로 역직렬화
        // 즉, 현재 설정만 보면 메시지를 바로 객체로 변환하는 구조는 아니다.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    private DefaultErrorHandler createAuctionBidFeeChargeRequestedErrorHandler() {
        return new DefaultErrorHandler((record, exception) ->
                log.error("경매 입찰 보증금 요청 Kafka 리스너 최종 실패: topic={}, partition={}, offset={}, key={}, payloadSnippet={}",
                        record.topic(), record.partition(), record.offset(), record.key(), summarizePayload(record), exception),
                new FixedBackOff(0L, 0L));
    }

    /**
     * 판매자 정산 지급 요청 이벤트 처리 실패 시 사용할 에러 핸들러 생성
     * <p>
     * 동작 방식:
     * 1. 예외 발생
     * 2. 재시도 가능한 예외면 지수 백오프로 재시도
     * 3. 재시도 횟수 소진 시 DLQ로 발행
     * 4. 비재시도 예외는 즉시 DLQ로 보냄
     */
    private DefaultErrorHandler createPayoutRequestedErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        // 재시도 간격을 점점 늘리는 백오프 정책
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(KafkaRetryPolicy.MAX_RETRIES);
        backOff.setInitialInterval(KafkaRetryPolicy.INITIAL_INTERVAL_MS);
        backOff.setMultiplier(KafkaRetryPolicy.MULTIPLIER);
        backOff.setMaxInterval(KafkaRetryPolicy.MAX_INTERVAL_MS);

        // 재시도 끝까지 실패한 메시지를 DLQ 토픽으로 보내는 recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(KafkaTopics.SETTLEMENT_PAYOUT_REQUESTED_DLQ, record.partition())
        );

        // recoverer + backOff를 사용하는 에러 핸들러 생성
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 아래 예외는 재시도해도 성공 가능성이 낮다고 판단해서 즉시 DLQ 처리
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, WalletNotFoundException.class);

        return errorHandler;
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
