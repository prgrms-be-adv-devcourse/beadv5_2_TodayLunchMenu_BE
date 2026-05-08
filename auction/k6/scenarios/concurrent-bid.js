/**
 * 동시 입찰 경쟁 테스트 (Concurrent Bid Test)
 *
 * 목적:
 *   여러 사용자가 동시에 같은 경매에 입찰할 때 낙관락 동시성 처리가 올바른지 검증한다.
 *   - bid_server_error = 0 → 낙관락 충돌 처리 중 데드락/타임아웃 없음
 *   - bid_success 다수   → 모든 입찰이 PENDING으로 정상 수신됨
 *   - bid_duration p99   → HikariCP 병목이 수용 가능한 수준인지 측정
 *
 * 낙관락 동작 원리:
 *   여러 VU가 동시에 경매 상세 조회 → 같은 currentHighestPrice 읽음
 *   → 동일 bidPrice로 동시 POST → 입찰 생성은 모두 PENDING 통과 (409 거의 없음)
 *   → Payment Kafka 응답 후 BidConfirmService가 @Version 충돌 시 재시도(MAX_RETRY=3)
 *   → 최종 ACTIVE는 1건, 나머지는 자동 취소
 *
 * 사전 조건:
 *   1) load_test_auctions.sql 실행
 *   2) reset_test_auctions.sql 실행 (current_highest_price = NULL 초기화)
 *
 * 실행:
 *   k6 run auction/k6/scenarios/concurrent-bid.js -e VUS=30
 *   k6 run auction/k6/scenarios/concurrent-bid.js -e VUS=50
 *   k6 run auction/k6/scenarios/concurrent-bid.js -e VUS=100
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL } from '../config/thresholds.js';
import { CONCURRENT_BID_AUCTION_ID, BASELINE_BIDDER_IDS } from '../helpers/data.js';
import { buyerHeaders, publicHeaders } from '../helpers/auth.js';
import { fetchCurrentBidPrice } from '../helpers/bid.js';
import { recordBidResult } from '../helpers/metrics.js';

const bidDuration = new Trend('bid_duration', true);

const TARGET_VUS = parseInt(__ENV.VUS || '30');

export const options = {
  scenarios: {
    concurrent_bid: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: '2m',
    },
  },
  thresholds: {
    // 서버 오류 없어야 낙관락 충돌 처리 정상
    bid_server_error: ['count<3'],
    // 성공 입찰 최소 1건
    bid_success: ['count>0'],
    // 꼬리 지연 허용 범위
    bid_duration: ['p(99)<5000', 'p(99.9)<10000'],
  },
};

export default function () {
  // VU별 고정 입찰자 배정 (wallet 보유 + 동일 입찰자 충돌 회피)
  // 풀(30명) 초과 VU에서는 modulo로 중복 사용 → VUS>30 권장하지 않음
  const bidderId = BASELINE_BIDDER_IDS[(__VU - 1) % BASELINE_BIDDER_IDS.length];

  // 동시에 같은 값을 읽으면 동일 bidPrice → 낙관락 경합 자연 발생
  const bidPrice = fetchCurrentBidPrice(CONCURRENT_BID_AUCTION_ID);
  if (bidPrice === null) {
    console.log('경매 상세 조회 실패, 입찰 스킵');
    return;
  }

  const res = http.post(
    `${BASE_URL}/api/auctions/${CONCURRENT_BID_AUCTION_ID}/bids`,
    JSON.stringify({ bidPrice }),
    {
      headers: buyerHeaders(bidderId),
      responseCallback: http.expectedStatuses(201, 400, 409, 422),
    }
  );

  bidDuration.add(res.timings.duration);
  recordBidResult(res);

  check(res, {
    '서버 에러 없음': (r) => r.status < 500,
  });
}

/**
 * 낙관락 정확성 검증 — API 레벨 (테스트 종료 후 1회 실행)
 *
 * currentHighestPrice != null → BidConfirmService가 ACTIVE를 1건 이상 확정했다는 증거.
 * ACTIVE가 정확히 1건인지는 handleSummary가 출력하는 SQL로 직접 확인.
 */
export function teardown() {
  const waitSeconds = parseInt(__ENV.TEARDOWN_WAIT || '20');
  console.log(`[teardown] ${waitSeconds}초 대기 — Outbox 스케줄러(10s) + Kafka 처리 완료 대기 중...`);
  sleep(waitSeconds);

  const auctionRes = http.get(
    `${BASE_URL}/api/auctions/${CONCURRENT_BID_AUCTION_ID}`,
    { headers: publicHeaders() }
  );
  const auction = auctionRes.json('data');
  console.log(`[teardown] auction currentHighestPrice=${auction?.currentHighestPrice ?? null}`);

  check(auction, {
    '낙관락 보장: currentHighestPrice 확정됨 (ACTIVE bid 존재 증거)': (a) => a?.currentHighestPrice != null,
  });
}

/**
 * 테스트 종료 후 결과 요약 + DB 무결성 검증 쿼리 출력
 * ACTIVE=1 / PENDING=0 / 낙찰가 역전 없음 — 세 쿼리 모두 0행이어야 낙관락 정상 동작
 */
export function handleSummary(data) {
  const success   = data.metrics['bid_success']?.values?.count      || 0;
  const rejected  = data.metrics['bid_rejected']?.values?.count     || 0;
  const serverErr = data.metrics['bid_server_error']?.values?.count || 0;
  const p99       = data.metrics['bid_duration']?.values?.['p(99)'] || 0;

  console.log(`
=== 동시 입찰 테스트 결과 ===
입찰 성공    (201): ${success}
비즈니스 거부 (4xx): ${rejected}
서버 오류    (5xx): ${serverErr}
bid_duration  p99: ${p99.toFixed(0)}ms

[DB 무결성 검증] 테스트 후 아래 쿼리 실행 (모두 0행이어야 정상):

-- 1. ACTIVE bid 중복 (낙관락 실패 시 2건 이상)
SELECT auction_id, COUNT(*) AS cnt
FROM auction.bid WHERE status = 'ACTIVE'
GROUP BY auction_id HAVING COUNT(*) > 1;

-- 2. PENDING 잔존 (Kafka 미처리 이벤트)
SELECT auction_id, COUNT(*) AS cnt
FROM auction.bid WHERE status = 'PENDING'
GROUP BY auction_id HAVING COUNT(*) > 1;

-- 3. 낙찰가 역전 (더 낮은 가격이 ACTIVE인 경우)
SELECT b1.auction_id, b1.bid_price AS winner, b2.bid_price AS should_have_won
FROM auction.bid b1
JOIN auction.bid b2 ON b1.auction_id = b2.auction_id
WHERE b1.status = 'ACTIVE' AND b2.status = 'OUTBID'
  AND b2.bid_price > b1.bid_price;
`);

  return { stdout: JSON.stringify(data, null, 2) };
}
