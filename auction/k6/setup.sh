#!/bin/bash
set -e

PSQL="kubectl exec -i postgres-c99f56b7d-7bdts -n goods-mall -- psql -U goods -d goods_mall"
K6_DIR=~/k6

kafka_reset() {
  local group="$1"
  local topic="$2"
  local kafka_pod
  kafka_pod=$(kubectl get pods -n goods-mall -l app=kafka -o jsonpath='{.items[0].metadata.name}')
  kubectl exec -i "$kafka_pod" -n goods-mall -- \
    kafka-consumer-groups \
      --bootstrap-server localhost:9092 \
      --group "$group" \
      --topic "$topic" \
      --reset-offsets --to-latest --execute
}

echo "===== [0/7] auction/payment 스케일다운 (Kafka consumer 비활성화) ====="
kubectl scale deployment auction payment -n goods-mall --replicas=0
kubectl wait pod --for=delete -l app=auction -n goods-mall --timeout=90s || true
kubectl wait pod --for=delete -l app=payment -n goods-mall --timeout=90s || true

echo "===== [1/7] 기존 테스트 데이터 클린업 (auction + payment DB) ====="
$PSQL < $K6_DIR/seed/cleanup_test_data.sql

echo "===== [2/7] Kafka 오프셋 리셋 — auction-service (payment 결과 토픽) ====="
kafka_reset auction-service payment.bid-fee.charge.succeeded
kafka_reset auction-service payment.bid-fee.charge.failed

echo "===== [3/7] Kafka 오프셋 리셋 — payment-service (auction 요청 토픽) ====="
kafka_reset payment-service auction.bid-fee.charge.requested

echo "===== [4/7] 테스트 경매 시딩 (smoke/baseline용) ====="
$PSQL < $K6_DIR/seed/seed_test_auctions.sql

echo "===== [5/7] 베이스라인 입찰자 wallet 시딩 (30명) ====="
$PSQL < $K6_DIR/seed/seed_baseline_wallets.sql

echo "===== [6/7] load/stress/spike/soak/concurrent-bid 경매 시딩 ====="
$PSQL < $K6_DIR/seed/load_test_auctions.sql

echo "===== [7/7] auction/payment 스케일업 ====="
kubectl scale deployment auction payment -n goods-mall --replicas=1
kubectl rollout status deployment/auction -n goods-mall --timeout=120s
kubectl rollout status deployment/payment -n goods-mall --timeout=120s

echo "===== 셋업 완료 — run_all.sh 를 실행하세요 ====="
