package com.todaylunch.common.messaging.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Kafka Consumer 공통 설정 헬퍼.
 *
 * 모듈별로 중복 작성되던 ConsumerFactory props 생성 로직을 통합.
 * - bootstrap-servers, group-id : 인자로 주입
 * - auto-offset-reset           : "earliest" 기본
 * - key/value deserializer       : String 기본
 *
 * 모듈별 추가 설정이 필요하면 반환된 Map에 put으로 override 가능.
 *
 * <pre>{@code
 * Map<String, Object> props = KafkaConsumerProps.defaults(bootstrapServers, groupId);
 * props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);  // override
 * return new DefaultKafkaConsumerFactory<>(props);
 * }</pre>
 */
public final class KafkaConsumerProps {

    private KafkaConsumerProps() {
    }

    /**
     * 표준 String K/V Consumer props.
     *
     * @param bootstrapServers Kafka broker 주소
     * @param groupId          consumer group id (KafkaConsumerGroups 상수 권장)
     */
    public static Map<String, Object> defaults(String bootstrapServers, String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
