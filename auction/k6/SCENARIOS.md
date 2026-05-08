# 경매 서비스 부하테스트 시나리오

## 전체 흐름

```
[DB 초기화]
  └─ load_test_auctions.sql  (최초 1회)
  └─ reset_test_auctions.sql (매 시나리오 시작 전)

[테스트 순서]
  smoke → baseline → concurrent-bid → load → spike → stress → soak
```

> **매 시나리오 전 DB 리셋 필수**
> 입찰이 누적되면 `current_highest_price`가 올라가 이후 시나리오의 입찰가 계산 기준이 달라짐.

---

## 시스템 제약 (설계 근거)

| 항목 | 값 | 테스트 설계 영향 |
|---|---|---|
| HikariCP | min 2 / max 5 | **예상 병목** — 동시 입찰 5건 초과 시 대기 시작 |
| 입찰 락 | 낙관락 (@Version) | 충돌 시 BidConfirmService retry(MAX=3), 초과 시 자동 취소 |
| 비동기 확정 | Outbox → Kafka → Payment | 입찰 201 후 PENDING 상태, Payment 없으면 확정 불가 |
| 스케줄러 | 10초 주기 | 경매 상태 전이 자동화 (ONGOING → ENDED 등) |

---

## 입찰가 동적 계산 원리

모든 시나리오의 입찰은 고정 금액이 아닌 **조회 → 계산** 패턴을 사용한다.

```
GET /api/auctions/{id}
  └─ currentHighestPrice = X (또는 null)
  └─ bidPrice = max(startPrice, X + bidUnit)

POST /api/auctions/{id}/bids  { bidPrice }
```

**락 경합 발생 원리**: 여러 VU가 동시에 같은 경매를 조회하면 같은 `currentHighestPrice`를 읽는다.
같은 `bidPrice`로 동시에 POST → 입찰 생성은 모두 PENDING 통과(201) → Payment Kafka 응답 후
낙관락(@Version) 충돌 시 재시도, 최종 ACTIVE는 1건, 나머지 자동 취소.
`pending_overrun` = 헛돈 Payment 호출 수 = `successful_bids - 1` 이 핵심 지표.

---

## 경매 데이터 풀

| 용도 | auction_id | 비고 |
|---|---|---|
| smoke / baseline 전용 | `eeeeeeee-eeee-eeee-eeee-eeeeeeeee001` | startPrice=50000, bidUnit=1000 |
| 부하테스트 풀 (B/C/D/E) | `aaaaaaaa-...-aaaaaaaaa101` ~ `105` | 5개, 동일 조건 |
| 락경합 테스트 전용 (A) | `aaaaaaaa-...-aaaaaaaaa201` | 단일 경매 집중 |

---

## Smoke Test

**목적**: 부하 테스트 전 기본 기능 정상 동작 확인

### 설정

| 항목 | 값 |
|---|---|
| VU | 2 |
| 시간 | 1분 |
| 대상 | ONGOING 시드 경매 1개 |

### 트래픽 패턴 (1 VU 기준 1회 반복)

1. 경매 목록 조회 `GET /api/auctions?status=ONGOING`
2. 경매 상세 조회 `GET /api/auctions/{id}` → `currentHighestPrice` 파싱
3. 입찰 목록 조회 `GET /api/auctions/{id}/bids`
4. 입찰 `POST /api/auctions/{id}/bids` — 상세 조회 결과로 bidPrice 계산

### 성공 기준

| 지표 | 기준 |
|---|---|
| `http_req_duration` p99 | < 2000ms |
| `http_req_failed` | < 1% |
| 입찰 응답 | 201 / 400 / 409 / 422만 허용 (5xx 없어야 함) |

### 실행

```bash
k6 run ~/k6/scenarios/smoke.js
```

---

## Baseline Test (기준선 측정)

**목적**: 정상 부하 구간에서 p50/p90/p99 측정 → 이후 시나리오 비교 기준

### 설정

| 항목 | 값 |
|---|---|
| VU | warm-up 10 → 기준 30 → cool-down 0 |
| 시간 | 1m + 3m + 1m = 5분 |
| 대상 | ONGOING 시드 경매 1개 |

### 트래픽 패턴

| 비율 | 요청 | 메트릭 |
|---|---|---|
| 50% | 목록 조회 | `list_duration` |
| 30% | 상세 조회 | `detail_duration` |
| 20% | 입찰 | `bid_duration` |

sleep 0.5s per iteration.

### 성공 기준

| 메트릭 | p90 | p99 |
|---|---|---|
| `bid_duration` | < 500ms | < 2000ms |
| `list_duration` | < 300ms | < 1000ms |
| `detail_duration` | < 200ms | < 500ms |

### 실행

```bash
k6 run ~/k6/scenarios/baseline.js --out json=results/baseline.json
```

---

## 시나리오 A: 동시 입찰 경쟁 테스트

**목적**: 낙관락 환경에서 pending_overrun(헛 Payment 호출) 규모 측정, HikariCP pool 소진 시점 확인

### 설정

| 항목 | 값 |
|---|---|
| VU | 30 / 50 / 100 (단계별 실행) |
| 시간 | 2분 (constant-vus) |
| 대상 | 락경합 전용 단일 경매 (`aaaaaaaaa201`) |

### 트래픽 패턴 (1 VU 1회)

```
fetchCurrentBidPrice(auctionId)  ← 동시에 같은 값을 읽음
  └─ GET /api/auctions/{id}

POST /api/auctions/{id}/bids  { bidPrice: currentHighestPrice + bidUnit }
```

동시에 여러 VU가 같은 bidPrice를 계산 → DB 락 경합 자연 발생.

### 측정 지표

| 메트릭 | 의미 |
|---|---|
| `bid_success` | 입찰 성공 (201) 누적 |
| `bid_rejected` | 비즈니스 규칙 거부 (400/409/422) 누적 — 높아도 정상, 동시성 보호 로직이 작동한다는 신호 |
| `bid_server_error` | 서버 오류 (5xx) 누적 — 0이어야 정상. 락 데드락/타임아웃 없음을 의미 |
| `bid_duration` p99/p99.9 | 락 직렬화로 인한 꼬리 지연 — VU 증가 시 급등 예상 |

> **테스트 종료 후 DB 무결성 쿼리 필수**: k6 메트릭만으로는 동시성 정확성을 완전히 검증할 수 없다.
> 테스트 종료 시 `handleSummary`가 자동으로 콘솔에 검증 쿼리를 출력한다.
> PENDING 중복 / ACTIVE 중복 / 낙찰가 역전 — 세 쿼리 모두 0행이어야 동시성 처리 정상.

### 예상 현상

- 동시 입찰이 모두 PENDING 통과 → `successful_bids` ≈ 요청 수
- `pending_overrun` 이 곧 시스템 비효율의 척도 (100 VU 2분이면 수천 건의 헛 Payment 호출 예상)
- VU 5 초과 → HikariCP 대기 시작 → `bid_duration` p99 급등 (락 직렬화)
- VU 100 이상 → 커넥션 큐 적체로 p99.9 가 5~10초대 진입

### 성공 기준

| 지표 | 기준 |
|---|---|
| `successful_bids` count | > 0 (한계 검증용) |
| `bid_duration` p99 | < 5s |
| `bid_duration` p99.9 | < 10s |
| `http_req_failed` | < 50% (5xx 기준, 4xx 비즈니스 에러는 제외) |
| 5xx 패턴 | 없거나 503/504 → HikariCP 포화일 때 별도 명시 |

### 실행

```bash
# DB 리셋 후 각 VU 단계별 실행 (VU당 별도 리셋 권장)
k6 run ~/k6/scenarios/concurrent-bid.js -e VUS=30 --out json=results/concurrent-bid-30.json
k6 run ~/k6/scenarios/concurrent-bid.js -e VUS=50 --out json=results/concurrent-bid-50.json
k6 run ~/k6/scenarios/concurrent-bid.js -e VUS=100 --out json=results/concurrent-bid-100.json
```

---

## 시나리오 B: 부하 테스트

**목적**: 읽기/입찰 혼합 실제 사용 패턴에서 안정성 측정

### 설정

| 항목 | 값 |
|---|---|
| VU | ramp-up 20 → 유지 50 → ramp-down 0 |
| 시간 | 2m + 5m + 2m = 9분 |
| 대상 | 경매 풀 6개 (시드 1 + 부하테스트 5) |

### 트래픽 패턴

| 비율 | 요청 | sleep |
|---|---|---|
| 40% | 목록 조회 `GET /api/auctions` | 0.3s |
| 30% | 상세 조회 `GET /api/auctions/{id}` | 0.2s |
| 30% | 입찰 `fetchCurrentBidPrice` → `POST /bids` | 1s |

### 성공 기준

| 메트릭 | p90 | p99 |
|---|---|---|
| `load_bid_duration` | < 500ms | < 2000ms |
| `load_read_duration` | < 300ms | < 1000ms |
| `load_error_rate` (5xx) | — | < 1% |

### 실행

```bash
k6 run ~/k6/scenarios/load.js --out json=results/load.json
```

---

## 시나리오 C: 스파이크 테스트

**목적**: 경매 마감 직전 폭발적 트래픽 재현, 회복 시간 측정

### 설정

| 구간 | VU | 시간 |
|---|---|---|
| 평상시 | 10 | 2분 |
| 스파이크 시작 | 10 → 100 | 10초 |
| 스파이크 유지 | 100 | 30초 |
| 해소 | 100 → 10 | 10초 |
| 회복 관찰 | 10 | 2분 |
| 종료 | 0 | 1분 |

> 스파이크 효과는 VU 수 증가 자체로 결정. 모든 VU 동일 패턴 사용 (30% 입찰 / 70% 조회).

### 트래픽 패턴

| 비율 | 요청 |
|---|---|
| 30% | 입찰 `fetchCurrentBidPrice` → `POST /bids` |
| 70% | 상세 조회 `GET /api/auctions/{id}` + sleep 0.5s |

### 측정 포인트

- 스파이크 시작 시 `spike_bid_duration` 급등 여부
- 스파이크 해소 후 응답시간 회복 속도
- `bids_during_spike` 카운터로 스파이크 구간 입찰 총량 확인

### 성공 기준

| 메트릭 | 기준 |
|---|---|
| `spike_error_rate` (5xx) | < 10% |
| `http_req_failed` | < 10% |

### 실행

```bash
k6 run ~/k6/scenarios/spike.js --out json=results/spike.json
```

---

## 시나리오 D: 스트레스 테스트

**목적**: 시스템 포화점(Saturation Point) 탐색 — 에러율 5% 초과 VU 수가 한계점

### 설정

| 구간 | VU | 시간 |
|---|---|---|
| 1단계 | 20 | 2분 |
| 2단계 | 50 | 2분 |
| 3단계 | 100 | 2분 |
| 4단계 | 200 | 2분 |
| 회복 | 0 | 2분 |

### 트래픽 패턴

| 비율 | 요청 |
|---|---|
| 40% | 목록 조회 |
| 30% | 상세 조회 |
| 30% | 입찰 `fetchCurrentBidPrice` → `POST /bids` |

sleep 없음 — 최대 처리량 측정이 목적.

### 측정 포인트

- 각 VU 단계별 `stress_5xx_rate` 추이 — 5% 초과 구간이 실질적 한계점
- `stress_status_503` / `stress_status_504` / `stress_status_500` 카운터 비교로 포화 원인 진단
  - 503 우세 → HikariCP 커넥션 풀 포화 (pool size 증설 검토)
  - 504 우세 → 락 대기 / 타임아웃 (트랜잭션 범위 또는 동시성 결함)
  - 500 발생 → 코드 예외 (부하와 무관, 즉시 수정 대상)
- `stress_bid_duration` p99 급등 시점 = 락/커넥션 포화 시점

### 성공 기준

임계값을 느슨하게 설정 — **통과가 목적이 아닌 한계점 탐색이 목적**.

| 메트릭 | 기준 |
|---|---|
| `stress_5xx_rate` | < 50% (탐색 범위 확보) |
| `stress_status_500` | == 0 (코드 예외는 부하와 무관, 발생 시 즉시 수정) |
| `http_req_failed` | < 50% |

### 실행

```bash
k6 run ~/k6/scenarios/stress.js --out json=results/stress.json
```

---

## 시나리오 E: 장기 안정성 테스트 (Soak)

**목적**: 메모리 누수, DB 커넥션 누수, Kafka 이벤트 누적 이상 확인

### 설정

| 항목 | 값 |
|---|---|
| VU | ramp-up → 유지 → ramp-down (기본 20 VU, 환경변수로 조정) |
| 유지 시간 | 기본 30분 (환경변수 DURATION으로 조정) |
| 대상 | 경매 풀 6개 |

> **목적별 권장 duration**
> - 시연/발표용 (빠른 안정성 확인): 30분 (default)
> - 누수 탐지 (메모리/커넥션/Kafka lag): **최소 2시간**, 권장 4~8시간
>   — 누수 패턴은 보통 2시간 이후에 드러남. 30분 통과 ≠ 누수 없음.

### 트래픽 패턴

| 비율 | 요청 | sleep |
|---|---|---|
| 50% | 목록 조회 | 1s |
| 30% | 상세 조회 | 0.5s |
| 20% | 입찰 `fetchCurrentBidPrice` → `POST /bids` | 2s |

sleep이 상대적으로 길다 — 낮은 TPS로 장시간 유지해 누수 탐지.

### Grafana 모니터링 항목 (병행 필수)

- JVM Heap Used — 지속 우상향 시 메모리 누수 의심
- HikariCP Active/Idle 커넥션 — Active가 회복 안 되면 커넥션 누수
- Kafka consumer lag — 지속 증가 시 Outbox 처리 지연

### 성공 기준

| 메트릭 | 기준 |
|---|---|
| `soak_error_rate` | < 2% |
| `http_req_failed` | < 2% |
| `http_req_duration` p99 | < 3000ms |
| JVM Heap | 시간 경과에 따른 지속 증가 없어야 함 |

### 실행

```bash
# 기본 (20 VU, 30분) — 시연용
k6 run ~/k6/scenarios/soak.js --out json=results/soak.json

# 누수 탐지용 (4시간)
k6 run ~/k6/scenarios/soak.js -e DURATION=4h -e VUS=30 --out json=results/soak.json

# 사용자 지정
k6 run ~/k6/scenarios/soak.js -e DURATION=60m -e VUS=30 --out json=results/soak.json
```

---

## 전체 실행 체크리스트

```
□ load_test_auctions.sql 삽입 확인 (최초 1회)
□ reset_test_auctions.sql 실행 (auction.bid + outbox_event + 최고가 초기화)
□ auction 서비스 ONGOING 상태 확인
□ Grafana 대시보드 열기 (soak 시에는 필수)

□ smoke      → 4개 엔드포인트 기능 검증
□ baseline   → p90/p99 기준선 기록
□ reset DB
□ concurrent-bid VUS=30 → 50 → 100   (VU당 reset 권장)
□ reset DB
□ load       → 혼합 부하 안정성 확인
□ reset DB
□ spike      → 스파이크 내성 및 회복 확인
□ reset DB
□ stress     → 포화점 VU 수 기록
□ reset DB

□ wallet 잔액 ≥ 50만원 확인 (psql SELECT balance FROM payment.wallet ...)
   → 부족하면 refill_wallet.sql 실행
□ soak       → 장기 누수 여부 확인 (Grafana 병행)

□ 모든 시나리오 종료 후 → cleanup_test_data.sql 실행 (테스트 데이터 + wallet 잔액 복원)
```

---

## 결과 분석 기준

### 입찰 레이턴시 해석

| p99 범위 | 해석 |
|---|---|
| < 500ms | 정상 — HikariCP 대기 없음 |
| 500ms ~ 2s | 경고 — HikariCP 대기 시작 또는 락 경합 |
| 2s ~ 5s | 위험 — DB 커넥션 포화 또는 락 타임아웃 임박 |
| > 5s | 한계 초과 |

### `bid_server_error` 해석 (concurrent-bid 핵심 지표)

| 값 | 해석 |
|---|---|
| 0 | 비관적 락이 데드락/타임아웃 없이 정상 직렬화 처리됨 |
| 1~3 | 간헐적 타임아웃 — 락 대기 시간 또는 HikariCP pool 크기 검토 |
| > 3 | 락 구현 문제 또는 커넥션 포화 — 즉시 원인 분석 필요 |

### DB 무결성 검증 (concurrent-bid 필수)

테스트 종료 시 자동으로 출력되는 쿼리를 DB에서 실행한다. 세 쿼리 모두 0행이어야 정상.

```sql
-- 1. 동시에 PENDING이 2개 이상 생성된 경우
SELECT auction_id, COUNT(*) FROM auction.bid
WHERE status = 'PENDING' GROUP BY auction_id HAVING COUNT(*) > 1;

-- 2. 중복 낙찰 (ACTIVE가 2개 이상)
SELECT auction_id, COUNT(*) FROM auction.bid
WHERE status = 'ACTIVE' GROUP BY auction_id HAVING COUNT(*) > 1;

-- 3. 낙찰가 역전 (낮은 가격이 낙찰된 경우)
SELECT b1.auction_id, b1.bid_price AS winner, b2.bid_price AS should_have_won
FROM auction.bid b1 JOIN auction.bid b2 ON b1.auction_id = b2.auction_id
WHERE b1.status = 'ACTIVE' AND b2.status = 'OUTBID' AND b2.bid_price > b1.bid_price;
```

### 5xx 에러 원인 분류

| 에러 | 예상 원인 |
|---|---|
| 500 Internal Error | 비즈니스 로직 예외 (NPE, 상태 불일치 등) |
| 503 Service Unavailable | HikariCP 커넥션 타임아웃 |
| 504 Gateway Timeout | 서비스 응답 지연 (락 대기 초과) |

---

## 수정 검토 필요 항목

> 현재 설계에서 확인/수정이 필요할 수 있는 부분

1. **입찰 가능 회원 조건**: `X-Member-Role: BUYER` 헤더로 입찰 요청 — 실제 서버 측 역할 검증 로직과 일치하는지 확인 필요
2. **bidPrice 충돌 허용 범위**: 동시에 같은 bidPrice 요청 시 409 외에 400이 올 수 있는지 — 비즈니스 에러 응답 코드 스펙 확인
3. **부하테스트 경매의 product_id**: 현재 `dddddddd-...-ddddddddd010` 사용 — 해당 product가 DB에 존재하는지 확인
4. **wallet 잔액 고갈**: 입찰 성공마다 예치금이 차감되면 장기 테스트에서 잔액 부족 오류 가능 — soak 전 wallet 충전 필요 여부 확인
5. **Outbox/Kafka 처리**: 입찰 201 성공이 쌓이면 Outbox 레코드가 누적됨 — soak 테스트 중 Kafka consumer lag 모니터링
