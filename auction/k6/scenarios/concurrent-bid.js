/**
 * 동시 입찰 경쟁 테스트 (Concurrent Bid Test)
 *
 * 목적:
 *   여러 사용자가 동시에 같은 경매에 경쟁 입찰할 때 실제 경매 진행이 올바른지 검증한다.
 *   - 경매 가격이 실제로 올라가는가 (낙찰가 진행)
 *   - ACTIVE는 항상 1건인가 (중복 없음)
 *   - 낙찰가 역전이 없는가 (더 낮은 가격이 ACTIVE가 되지 않는가)
 *   - OUTBID 처리가 올바른가 (이전 최고입찰자 환불)
 *   - 5xx 없음 (낙관락 충돌이 서버 오류로 번지지 않음)
 *
 * 입찰가 다양화 전략:
 *   모든 VU가 동일 최솟값으로 입찰하면 동일가격 충돌만 발생하고 경매가 진행되지 않는다.
 *   각 VU는 minBidPrice + random(0~4) × bidUnit 으로 서로 다른 입찰가를 제출한다.
 *   → 동시 입찰에서도 더 높은 가격이 승리하여 경매가 실질적으로 진행됨.
 *
 * 정상 동작 기준:
 *   OUTBID ≫ CANCELED  → 경매가 제대로 진행됨
 *   CANCELED 다수       → 여전히 동일가격 충돌 비중이 높음 (variance 증가 검토)
 *
 * 실행:
 *   k6 run auction/k6/scenarios/concurrent-bid.js -e RATE=10
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL } from '../config/thresholds.js';
import { CONCURRENT_BID_AUCTION_ID, BASELINE_BIDDER_IDS } from '../helpers/data.js';
import { buyerHeaders, publicHeaders } from '../helpers/auth.js';
import { fetchAuctionState } from '../helpers/bid.js';
import { recordBidResult } from '../helpers/metrics.js';

const bidDuration = new Trend('bid_duration', true);

const TARGET_RATE = parseInt(__ENV.RATE || '10');

export const options = {
  scenarios: {
    concurrent_bid: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RATE,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 50,
      maxVUs: 150,
    },
  },
  thresholds: {
    bid_server_error: ['count<3'],
    bid_success: ['count>0'],
    bid_duration: ['p(99)<5000', 'p(99.9)<10000'],
  },
};

export default function () {
  const bidderId = BASELINE_BIDDER_IDS[(__VU - 1) % BASELINE_BIDDER_IDS.length];

  const state = fetchAuctionState(CONCURRENT_BID_AUCTION_ID);
  if (state === null) {
    console.log('경매 상세 조회 실패, 입찰 스킵');
    return;
  }

  // VU마다 다른 입찰가: minBidPrice + 0~9 × bidUnit
  // → 10단계 분산으로 동일가격 충돌 감소, 더 높은 입찰이 ACTIVE 쟁취
  const extra = Math.floor(Math.random() * 10);
  const bidPrice = state.minBidPrice + (state.bidUnit * extra);

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

export function teardown() {
  const waitSeconds = parseInt(__ENV.TEARDOWN_WAIT || '120');
  console.log(`[teardown] ${waitSeconds}초 대기 — Outbox + Kafka 처리 완료 대기 중...`);
  sleep(waitSeconds);

  const auctionRes = http.get(
    `${BASE_URL}/api/auctions/${CONCURRENT_BID_AUCTION_ID}`,
    { headers: publicHeaders() }
  );
  const auction = auctionRes.json('data');
  const currentHighestPrice = auction?.currentHighestPrice ?? null;
  console.log(`[teardown] currentHighestPrice=${currentHighestPrice}, startPrice=${auction?.startPrice}`);

  check(auction, {
    '경매 진행 확인: currentHighestPrice 확정됨': (a) => a?.currentHighestPrice != null,
    '경매 실질 진행: 시작가 초과': (a) => (a?.currentHighestPrice ?? 0) > (a?.startPrice ?? 0),
  });
}

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

[DB 무결성 검증] 테스트 후 아래 쿼리 실행:

-- 1. ACTIVE bid 중복 (0행이어야 정상)
SELECT auction_id, COUNT(*) AS cnt
FROM auction.bid WHERE status = 'ACTIVE'
GROUP BY auction_id HAVING COUNT(*) > 1;

-- 2. 낙찰가 역전 (0행이어야 정상)
SELECT b1.auction_id, b1.bid_price AS winner, b2.bid_price AS should_have_won
FROM auction.bid b1
JOIN auction.bid b2 ON b1.auction_id = b2.auction_id
WHERE b1.status = 'ACTIVE' AND b2.status = 'OUTBID'
  AND b2.bid_price > b1.bid_price;

-- 3. bid 상태 분포 (OUTBID > CANCELED 이면 경매가 실질 진행된 것)
SELECT status, COUNT(*) AS cnt
FROM auction.bid
WHERE auction_id = '${CONCURRENT_BID_AUCTION_ID}'
GROUP BY status ORDER BY cnt DESC;

-- 4. PENDING 잔존 (0이어야 파이프라인 완료)
SELECT COUNT(*) AS pending_cnt
FROM auction.bid
WHERE auction_id = '${CONCURRENT_BID_AUCTION_ID}' AND status = 'PENDING';
`);

  return { stdout: JSON.stringify(data, null, 2) };
}
