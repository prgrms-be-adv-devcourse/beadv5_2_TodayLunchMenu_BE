package com.todaylunch.common.messaging.kafka;

/**
 * Kafka consumer 재시도/백오프 정책 상수.
 *
 * 모든 모듈의 DLQ ErrorHandler가 동일한 재시도 정책을 사용하도록 통합.
 * - 초기 간격: 1초
 * - 배수: 2.0
 * - 최대 간격: 10초
 * - 최대 재시도 횟수: 3회 → 총 약 7초의 backoff 후 DLQ 발행
 */
public final class KafkaRetryPolicy {

    public static final long INITIAL_INTERVAL_MS = 1000L;
    public static final double MULTIPLIER = 2.0;
    public static final long MAX_INTERVAL_MS = 10000L;
    public static final int MAX_RETRIES = 3;

    private KafkaRetryPolicy() {
    }
}
