package com.todaylunch.common.messaging.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka consumer의 공통 DLQ 에러 핸들러 생성 헬퍼.
 *
 * 동작 흐름:
 * 1. 처리 실패 시 {@link KafkaRetryPolicy}에 따라 ExponentialBackOff로 자동 재시도
 *    (1초 → 2초 → 4초, 최대 3회)
 * 2. 재시도 소진 시 dlqTopic으로 메시지 발행하여 무손실 보장
 * 3. notRetryableExceptions에 등록된 예외는 재시도 없이 즉시 DLQ로 발행
 *
 * 기존에 payment/settlement 모듈에서 중복으로 작성되던 DLQ ErrorHandler 로직을
 * 단일 정책 출처로 통합.
 */
public final class DlqErrorHandlerFactory {

    private DlqErrorHandlerFactory() {
    }

    /**
     * DLQ + ExponentialBackOff 적용한 ErrorHandler를 생성한다.
     *
     * @param kafkaTemplate         DLQ 발행에 사용할 KafkaTemplate
     * @param dlqTopic              실패 메시지를 격리할 DLQ 토픽 이름
     * @param notRetryableExceptions 재시도 무의미한 예외 (검증 실패 등) — 즉시 DLQ로 발행
     */
    @SafeVarargs
    public static DefaultErrorHandler create(
            KafkaTemplate<String, String> kafkaTemplate,
            String dlqTopic,
            Class<? extends Exception>... notRetryableExceptions
    ) {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(KafkaRetryPolicy.MAX_RETRIES);
        backOff.setInitialInterval(KafkaRetryPolicy.INITIAL_INTERVAL_MS);
        backOff.setMultiplier(KafkaRetryPolicy.MULTIPLIER);
        backOff.setMaxInterval(KafkaRetryPolicy.MAX_INTERVAL_MS);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(dlqTopic, record.partition())
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        if (notRetryableExceptions != null && notRetryableExceptions.length > 0) {
            errorHandler.addNotRetryableExceptions(notRetryableExceptions);
        }
        return errorHandler;
    }
}
