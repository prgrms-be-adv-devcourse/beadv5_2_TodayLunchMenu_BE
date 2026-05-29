package com.example.product.infrastructure.messaging.kafka;

/**
 * product 모듈의 Kafka consumer group-id 상수.
 *
 * 타입 안전성과 IDE 자동완성을 위해 상수로 관리.
 * 환경별 변경이 필요하면 Kafka cluster 자체를 분리.
 */
public final class KafkaConsumerGroups {

    public static final String PRODUCT_SERVICE = "product-service";

    private KafkaConsumerGroups() {
    }
}
