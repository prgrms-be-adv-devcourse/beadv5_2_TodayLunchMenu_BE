package com.todaylunch.auction.infrastructure.messaging.kafka;

import com.todaylunch.auction.domain.entity.OutboxEvent;
import com.todaylunch.auction.domain.enumtype.OutboxEventStatus;
import com.todaylunch.auction.domain.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOutbox() {
        List<OutboxEvent> pending = outboxEventRepository.findAllByStatus(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                event.changePublished();
                outboxEventRepository.save(event);
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                             .whenComplete((result, ex) -> {
                                 if (ex != null) {
                                     log.error("Outbox Kafka 발행 실패: id={}, topic={}", event.getId(), event.getTopic(), ex);
                                 } else {
                                     log.debug("Outbox Kafka 발행 성공: id={}, topic={}", event.getId(), event.getTopic());
                                 }
                             });
            } catch (Exception e) {
                log.error("Outbox Kafka 발행 예외: id={}, topic={}", event.getId(), event.getTopic(), e);
                event.revertToPending();
            }
        }
    }
}
