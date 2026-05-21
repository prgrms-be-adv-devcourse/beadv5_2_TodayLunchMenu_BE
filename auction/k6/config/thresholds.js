export const BID_THRESHOLDS = {
  http_req_duration: ['p(90)<500', 'p(99)<2000'],
  http_req_failed: ['rate<0.01'],
};

export const READ_THRESHOLDS = {
  http_req_duration: ['p(90)<300', 'p(99)<1000'],
  http_req_failed: ['rate<0.01'],
};

export const STRESS_THRESHOLDS = {
  http_req_duration: ['p(90)<1000', 'p(99)<5000'],
  http_req_failed: ['rate<0.05'],
};

// NodePort 접근 (infra 노드에서 실행 시) — app 노드 프라이빗 IP + auction NodePort
// 로컬 실행 시: http://localhost:8090
export const BASE_URL = 'http://172.31.6.164:30245';

// WebSocket(STOMP) 엔드포인트 — BASE_URL의 host:port 재사용
export const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/api/auctions/ws';

// 현재 미사용 — helpers/auth.js login() 전용.
// 게이트웨이 경유 테스트(JWT 발급/검증) 추가 시 사용.
export const MEMBER_URL = 'http://10.43.207.139:8083';
