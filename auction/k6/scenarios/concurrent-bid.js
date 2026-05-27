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
 *   k6 run auction/k6/scenarios/concurrent-bid.js -e VUS=10
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

const TARGET_VUS = parseInt(__ENV.VUS || '10');

export const options = {
  scenarios: {
    concurrent_bid: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: '2m',
    },
  },
  thresholds: {
    bid_server_error: ['count<3'],
    bid_success: ['count>0'],
    bid_duration: ['p(99)<5000', 'p(99.9)<10000'],
  },
  teardownTimeout: '180s',
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
  const waitSeconds = parseInt(__ENV.TEARDOWN_WAIT || '10');
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
  const statsRes = http.get(`${BASE_URL}/api/auctions/${CONCURRENT_BID_AUCTION_ID}/bids/stats`);
  const bidStats = statsRes.status === 200 ? (statsRes.json('data') || {}) : {};

  const success   = data.metrics['bid_success']?.values?.count      || 0;
  const rejected  = data.metrics['bid_rejected']?.values?.count     || 0;
  const serverErr = data.metrics['bid_server_error']?.values?.count || 0;
  const p99       = data.metrics['bid_duration']?.values?.['p(99)'] || 0;
  const p95       = data.metrics['bid_duration']?.values?.['p(95)'] || 0;
  const p50       = data.metrics['bid_duration']?.values?.['p(50)'] || 0;
  const total     = success + rejected + serverErr;

  const successPct   = total > 0 ? ((success   / total) * 100).toFixed(1) : '0.0';
  const rejectedPct  = total > 0 ? ((rejected  / total) * 100).toFixed(1) : '0.0';
  const serverErrPct = total > 0 ? ((serverErr / total) * 100).toFixed(1) : '0.0';

  const allPassed = Object.values(data.metrics['bid_server_error']?.thresholds || {}).every(t => t.ok)
                 && Object.values(data.metrics['bid_duration']?.thresholds    || {}).every(t => t.ok);

  const dbTotal     = Object.values(bidStats).reduce((s, v) => s + v, 0);
  const statusOrder = ['ACTIVE', 'OUTBID', 'PENDING', 'CANCELED', 'PAYMENT_COMPLETED'];
  const statusLabel = { ACTIVE: '낙찰 진행중', OUTBID: '역전됨', PENDING: '미처리 잔존', CANCELED: '취소/환불', PAYMENT_COMPLETED: '결제완료' };
  const statusColorMap = { ACTIVE: '#2563eb', OUTBID: '#7c3aed', PENDING: '#f59e0b', CANCELED: '#6b7280', PAYMENT_COMPLETED: '#16a34a' };

  const bar = (pct, color) => `
    <div style="display:flex;align-items:center;gap:8px;">
      <div style="flex:1;background:#e5e7eb;border-radius:4px;height:20px;overflow:hidden;">
        <div style="width:${pct}%;background:${color};height:100%;border-radius:4px;"></div>
      </div>
      <span style="min-width:48px;text-align:right;font-weight:600;">${pct}%</span>
    </div>`;

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>동시 입찰 테스트 결과</title>
  <style>
    body { font-family:'Segoe UI',sans-serif; background:#f8fafc; margin:0; padding:24px; color:#1e293b; }
    h1   { font-size:1.4rem; margin-bottom:4px; }
    .sub { color:#64748b; font-size:.85rem; margin-bottom:24px; }
    .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:16px; margin-bottom:24px; }
    .card { background:#fff; border-radius:10px; padding:18px 22px; box-shadow:0 1px 4px rgba(0,0,0,.08); }
    .card .label { font-size:.78rem; color:#64748b; margin-bottom:4px; text-transform:uppercase; letter-spacing:.05em; }
    .card .value { font-size:2rem; font-weight:700; }
    .card .hint  { font-size:.78rem; color:#94a3b8; margin-top:2px; }
    .section { background:#fff; border-radius:10px; padding:20px 24px; box-shadow:0 1px 4px rgba(0,0,0,.08); margin-bottom:16px; }
    .section h2 { font-size:1rem; margin:0 0 16px; color:#334155; }
    .row { display:grid; grid-template-columns:130px 1fr; align-items:center; gap:12px; margin-bottom:10px; font-size:.88rem; }
    .badge { display:inline-block; padding:2px 8px; border-radius:12px; font-size:.75rem; font-weight:600; }
    .pass { background:#dcfce7; color:#16a34a; }
    .fail { background:#fee2e2; color:#dc2626; }
    .sql  { background:#1e293b; color:#e2e8f0; border-radius:8px; padding:16px; font-family:monospace; font-size:.8rem; white-space:pre; overflow-x:auto; line-height:1.6; }
  </style>
</head>
<body>
  <h1>동시 입찰 부하 테스트 결과</h1>
  <div class="sub">VUS=${TARGET_VUS} · duration=2m (지속 동시 입찰) · auction=${CONCURRENT_BID_AUCTION_ID} &nbsp;|&nbsp; 임계값: <span class="badge ${allPassed ? 'pass' : 'fail'}">${allPassed ? 'PASS' : 'FAIL'}</span></div>

  <div class="grid">
    <div class="card">
      <div class="label">전체 입찰 시도</div>
      <div class="value">${total.toLocaleString()}</div>
      <div class="hint">HTTP 응답 기준</div>
    </div>
    <div class="card">
      <div class="label">입찰 접수 (201)</div>
      <div class="value" style="color:#2563eb;">${success.toLocaleString()}</div>
      <div class="hint">PENDING 생성 = ${successPct}%</div>
    </div>
    <div class="card">
      <div class="label">비즈니스 거부 (4xx)</div>
      <div class="value" style="color:#d97706;">${rejected.toLocaleString()}</div>
      <div class="hint">${rejectedPct}%</div>
    </div>
    <div class="card">
      <div class="label">서버 오류 (5xx)</div>
      <div class="value" style="color:${serverErr > 0 ? '#dc2626' : '#16a34a'};">${serverErr.toLocaleString()}</div>
      <div class="hint">${serverErrPct}%</div>
    </div>
  </div>

  <div class="section">
    <h2>입찰 상태 분포 (DB 실측) <span style="font-size:.78rem;color:#94a3b8;font-weight:400;">— 전체 ${dbTotal.toLocaleString()}건</span></h2>
    ${dbTotal === 0
      ? '<p style="color:#94a3b8;font-size:.85rem;">stats API 응답 없음</p>'
      : statusOrder
          .filter(s => bidStats[s] !== undefined)
          .map(s => {
            const cnt = bidStats[s] || 0;
            const pct = dbTotal > 0 ? ((cnt / dbTotal) * 100).toFixed(1) : '0.0';
            const color = statusColorMap[s];
            return `<div class="row">
              <span style="display:flex;align-items:center;gap:6px;">
                <span style="width:10px;height:10px;border-radius:50%;background:${color};display:inline-block;"></span>
                ${statusLabel[s] || s}
              </span>
              <div style="display:flex;align-items:center;gap:8px;">
                <div style="flex:1;background:#e5e7eb;border-radius:4px;height:20px;overflow:hidden;">
                  <div style="width:${pct}%;background:${color};height:100%;border-radius:4px;"></div>
                </div>
                <span style="min-width:110px;text-align:right;font-size:.85rem;">
                  <strong>${pct}%</strong> <span style="color:#94a3b8;">(${cnt.toLocaleString()}건)</span>
                </span>
              </div>
            </div>`;
          }).join('')
    }
  </div>

  <div class="section">
    <h2>응답 시간</h2>
    <div class="row"><span>p50</span><span style="font-weight:600;">${p50.toFixed(0)} ms</span></div>
    <div class="row"><span>p95</span><span style="font-weight:600;">${p95.toFixed(0)} ms</span></div>
    <div class="row"><span>p99</span><span style="font-weight:600;color:${p99 > 5000 ? '#dc2626' : '#1e293b'};">${p99.toFixed(0)} ms</span></div>
  </div>

  <div class="section">
    <h2>DB 무결성 검증 쿼리</h2>
    <p style="font-size:.85rem;color:#475569;margin-bottom:8px;">① ACTIVE bid 중복 (0행이어야 정상)</p>
    <div class="sql">SELECT auction_id, COUNT(*) AS cnt
FROM auction.bid WHERE status = 'ACTIVE'
GROUP BY auction_id HAVING COUNT(*) > 1;</div>
    <p style="font-size:.85rem;color:#475569;margin:14px 0 8px;">② 낙찰가 역전 (0행이어야 정상)</p>
    <div class="sql">SELECT b1.auction_id, b1.bid_price AS winner, b2.bid_price AS should_have_won
FROM auction.bid b1
JOIN auction.bid b2 ON b1.auction_id = b2.auction_id
WHERE b1.status = 'ACTIVE' AND b2.status = 'OUTBID'
  AND b2.bid_price > b1.bid_price;</div>
  </div>
</body>
</html>`;

  return {
    'stdout': `\n=== 동시 입찰 테스트 완료 ===\n접수(201): ${success} (${successPct}%)  거부(4xx): ${rejected}  오류(5xx): ${serverErr}  p99: ${p99.toFixed(0)}ms\n결과 리포트: report/concurrent-bid-report.html\n`,
    'report/concurrent-bid-report.html': html,
  };
}
