package com.todaylunch.common.messaging.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Kafka Producer 공통 설정 헬퍼.
 *
 * 모듈별로 중복 작성되던 ProducerFactory props 생성 로직을 통합.
 * - bootstrap-servers       : 인자로 주입
 * - key/value serializer    : String 기본
 *
 * 모듈별 추가 설정(acks, retries, idempotence 등)이 필요하면
 * 반환된 Map에 put으로 override 가능.
 *
 * <pre>{@code
 * Map<String, Object> props = KafkaProducerProps.defaults(bootstrapServers);
 * props.put(ProducerConfig.ACKS_CONFIG, "all");           // override
 * props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
 * return new DefaultKafkaProducerFactory<>(props);
 * }</pre>
 */
public final class KafkaProducerProps {

    private KafkaProducerProps() {
    }

    /**
     * 표준 String K/V Producer props.
     *
     * @param bootstrapServers Kafka broker 주소
     */
    public static Map<String, Object> defaults(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }
}
