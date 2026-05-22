package com.todaylunch.auction.infrastructure.messaging.kafka;

import com.todaylunch.auction.application.event.OutboxEventPendingTrigger;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxProcessor outboxProcessor;

    // 동시 입찰 시 매 commit마다 REQUIRES_NEW 트랜잭션이 생성되어 HikariCP 풀이 고갈되는 문제를 막는다.
    // 한 번에 한 스레드만 processOutbox()를 호출하고, 그 동안 들어온 트리거는 즉시 반환한다.
    // 누락된 이벤트는 다음 commit 트리거 또는 5초 스케줄러가 처리한다.
    private final AtomicBoolean processing = new AtomicBoolean(false);

    // 트랜잭션 커밋 직후 즉시 실행 — 지연 없이 Kafka로 발행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventCreated(OutboxEventPendingTrigger trigger) {
        process();
    }

    // 누락된 PENDING 이벤트 백업 처리 (5초 폴링)
    @Scheduled(fixedDelay = 5000)
    public void scheduledRelay() {
        process();
    }

    private void process() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }
        try {
            outboxProcessor.processOutbox();
        } finally {
            processing.set(false);
        }
    }
}
