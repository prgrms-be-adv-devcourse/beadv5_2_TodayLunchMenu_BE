/**
 * 시나리오 D: 스트레스 테스트 (Stress Test)
 * 목적: 시스템 포화점(Saturation Point) 탐색
 *       에러율 5% 초과 시점 = 한계점
 * 실행: k6 run auction/k6/scenarios/stress.js --out json=results/stress.json
 * 전제: reset_test_auctions.sql 실행 후 시작
 *
 * VU 스케일 근거 (HikariCP max=5):
 *   50 VU → 이미 DB 커넥션 경합 시작
 *   100 VU → 포화 예상 구간
 *   200 VU → 실질적 한계점 확인
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL } from '../config/thresholds.js';
import { SEED_AUCTIONS, LOAD_TEST_AUCTION_IDS, BASELINE_BIDDER_IDS } from '../helpers/data.js';
import { publicHeaders, buyerHeaders } from '../helpers/auth.js';
import { fetchCurrentBidPrice } from '../helpers/bid.js';

// 5xx 전체 비율 — 한계점 판정용 (5% 초과 구간이 실질적 한계점)
const error5xxRate = new Rate('stress_5xx_rate');
const bidTrend = new Trend('stress_bid_duration', true);

//   503: HikariCP 커넥션 풀 타임아웃 → pool size 증설 또는 락 시간 단축 검토
//   504: 락 대기 / 게이트웨이 타임아웃 → 트랜잭션 범위 또는 동시성 결함
//   500: 비즈니스 예외 (NPE, IllegalState 등) → 부하와 무관한 버그, 즉시 수정 대상
const status503 = new Counter('stress_status_503');
const status504 = new Counter('stress_status_504');
const status500 = new Counter('stress_status_500');

function record5xx(status) {
  const is5xx = status >= 500;
  error5xxRate.add(is5xx ? 1 : 0);
  if (status === 503) status503.add(1);
  else if (status === 504) status504.add(1);
  else if (status === 500) status500.add(1);
}

export const options = {
  stages: [
    { duration: '2m', target: 20 },
    { duration: '2m', target: 50 },
    { duration: '2m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '2m', target: 0 },  // 회복
  ],
  thresholds: {
    // 임계값을 느슨하게 설정 (한계점 탐색이 목적)
    stress_5xx_rate: ['rate<0.50'],
    // 500 은 부하와 무관한 코드 버그 → 발생 시 빨간불
    stress_status_500: ['count==0'],
    http_req_failed: ['rate<0.50'],
  },
};

const AUCTION_IDS = [SEED_AUCTIONS.ONGOING, ...LOAD_TEST_AUCTION_IDS];

export default function () {
  const rand = Math.random();
  const auctionId = AUCTION_IDS[Math.floor(Math.random() * AUCTION_IDS.length)];

  if (rand < 0.40) {
    const res = http.get(
      `${BASE_URL}/api/auctions?page=0&size=9`,
      { headers: publicHeaders() }
    );
    record5xx(res.status);
    check(res, { 'list ok': (r) => r.status < 500 });

  } else if (rand < 0.70) {
    const res = http.get(
      `${BASE_URL}/api/auctions/${auctionId}`,
      { headers: publicHeaders() }
    );
    record5xx(res.status);
    check(res, { 'detail ok': (r) => r.status < 500 });

  } else {
    // 30% 입찰 (wallet 보유 시드 입찰자 풀에서 VU별 배정)
    // 200 VU > 풀 30명: 동일 입찰자가 여러 VU에 할당되어 422가 자주 나오나
    // stress는 한계점 탐색이라 도메인 충돌도 부하의 일부로 간주
    const bidderId = BASELINE_BIDDER_IDS[(__VU - 1) % BASELINE_BIDDER_IDS.length];
    const bidPrice = fetchCurrentBidPrice(auctionId);
    if (bidPrice !== null) {
      const res = http.post(
        `${BASE_URL}/api/auctions/${auctionId}/bids`,
        JSON.stringify({ bidPrice }),
        {
          headers: buyerHeaders(bidderId),
          responseCallback: http.expectedStatuses(201, 400, 409, 422),
        }
      );
      bidTrend.add(res.timings.duration);
      record5xx(res.status);
      check(res, { 'bid ok': (r) => r.status < 500 });
    }
  }
}
