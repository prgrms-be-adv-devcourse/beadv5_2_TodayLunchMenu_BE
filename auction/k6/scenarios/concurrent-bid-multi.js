/**
 * 멀티 경매 분산 부하 테스트 (Multi-Auction Concurrent Bid Test)
 *
 * 목적:
 *   여러 경매가 동시 진행되는 실 운영 환경을 시뮬레이션해
 *   Kafka partition key=auctionId 정책에서 throughput을 측정한다.
 *
 *   비교 대상:
 *     - concurrent-bid.js : 단일 경매 부하 집중 (단일 partition 직렬 처리)
 *     - 이 시나리오       : 6개 경매에 부하 분산 (multiple partition 병렬 처리)
 *
 * 실행:
 *   k6 run auction/k6/scenarios/concurrent-bid-multi.js -e VUS=30 -e TEARDOWN_WAIT=120
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL } from '../config/thresholds.js';
import { SEED_AUCTIONS, LOAD_TEST_AUCTION_IDS, BASELINE_BIDDER_IDS } from '../helpers/data.js';
import { buyerHeaders, publicHeaders } from '../helpers/auth.js';
import { fetchAuctionState } from '../helpers/bid.js';
import { recordBidResult } from '../helpers/metrics.js';

const bidDuration = new Trend('bid_duration', true);

const TARGET_VUS = parseInt(__ENV.VUS || '30');
const AUCTION_IDS = [SEED_AUCTIONS.ONGOING, ...LOAD_TEST_AUCTION_IDS];

export const options = {
  scenarios: {
    multi_concurrent_bid: {
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
  teardownTimeout: '300s',
};

export default function () {
  const bidderId  = BASELINE_BIDDER_IDS[(__VU - 1) % BASELINE_BIDDER_IDS.length];
  const auctionId = AUCTION_IDS[Math.floor(Math.random() * AUCTION_IDS.length)];

  const state = fetchAuctionState(auctionId);
  if (state === null) {
    console.log(`경매 상세 조회 실패, 입찰 스킵: ${auctionId}`);
    return;
  }

  const extra    = Math.floor(Math.random() * 10);
  const bidPrice = state.minBidPrice + (state.bidUnit * extra);

  const res = http.post(
    `${BASE_URL}/api/auctions/${auctionId}/bids`,
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
  console.log(`[teardown] ${waitSeconds}초 대기 — ${AUCTION_IDS.length}개 경매에 대한 Outbox + Kafka 처리 완료 대기 중...`);
  sleep(waitSeconds);
}

export function handleSummary(data) {
  const aggregatedStats = {};
  AUCTION_IDS.forEach(auctionId => {
    const statsRes = http.get(`${BASE_URL}/api/auctions/${auctionId}/bids/stats`);
    if (statsRes.status === 200) {
      const stats = statsRes.json('data') || {};
      Object.entries(stats).forEach(([status, count]) => {
        aggregatedStats[status] = (aggregatedStats[status] || 0) + count;
      });
    }
  });

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

  const dbTotal     = Object.values(aggregatedStats).reduce((s, v) => s + v, 0);
  const statusOrder = ['ACTIVE', 'OUTBID', 'PENDING', 'CANCELED', 'PAYMENT_COMPLETED'];
  const statusLabel = { ACTIVE: '낙찰 진행중', OUTBID: '역전됨', PENDING: '미처리 잔존', CANCELED: '취소/환불', PAYMENT_COMPLETED: '결제완료' };
  const statusColorMap = { ACTIVE: '#2563eb', OUTBID: '#7c3aed', PENDING: '#f59e0b', CANCELED: '#6b7280', PAYMENT_COMPLETED: '#16a34a' };

  const throughput = (success / 120).toFixed(1);

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>멀티 경매 분산 입찰 테스트 결과</title>
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
  </style>
</head>
<body>
  <h1>멀티 경매 분산 입찰 부하 테스트 결과</h1>
  <div class="sub">VUS=${TARGET_VUS} · duration=2m · 분산 대상: ${AUCTION_IDS.length}개 경매 &nbsp;|&nbsp; 임계값: <span class="badge ${allPassed ? 'pass' : 'fail'}">${allPassed ? 'PASS' : 'FAIL'}</span></div>

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
      <div class="label">처리 throughput</div>
      <div class="value" style="color:#16a34a;">${throughput}</div>
      <div class="hint">req/s (120초 기준)</div>
    </div>
    <div class="card">
      <div class="label">서버 오류 (5xx)</div>
      <div class="value" style="color:${serverErr > 0 ? '#dc2626' : '#16a34a'};">${serverErr.toLocaleString()}</div>
      <div class="hint">${serverErrPct}%</div>
    </div>
  </div>

  <div class="section">
    <h2>입찰 상태 분포 (DB 실측 — ${AUCTION_IDS.length}개 경매 합산) <span style="font-size:.78rem;color:#94a3b8;font-weight:400;">— 전체 ${dbTotal.toLocaleString()}건</span></h2>
    ${dbTotal === 0
      ? '<p style="color:#94a3b8;font-size:.85rem;">stats API 응답 없음</p>'
      : statusOrder
          .filter(s => aggregatedStats[s] !== undefined)
          .map(s => {
            const cnt = aggregatedStats[s] || 0;
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
    <h2>대상 경매 목록</h2>
    <ul style="font-family:monospace;font-size:.85rem;color:#475569;margin:0;padding-left:20px;">
      ${AUCTION_IDS.map(id => `<li>${id}</li>`).join('')}
    </ul>
  </div>
</body>
</html>`;

  return {
    'stdout': `\n=== 멀티 경매(${AUCTION_IDS.length}개) 동시 입찰 테스트 완료 ===\n` +
              `접수(201): ${success} (${successPct}%)  거부(4xx): ${rejected}  오류(5xx): ${serverErr}  p99: ${p99.toFixed(0)}ms\n` +
              `처리 throughput: ${throughput} req/s (120s 기준)\n` +
              `결과 리포트: report/concurrent-bid-multi-report.html\n`,
    'report/concurrent-bid-multi-report.html': html,
  };
}
