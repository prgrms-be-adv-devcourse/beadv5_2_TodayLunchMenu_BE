# K8s 환경변수 레퍼런스

각 서비스의 `application.yml`에서 참조하는 환경변수와 k8s ConfigMap / Secret 매핑 정리.

---

## 이번 변경사항 요약 (기존 설정 있는 분 필독)

아래 항목들이 하드코딩에서 환경변수 참조로 변경되었습니다.

| 서비스 | application.yml 항목 | 이전 (하드코딩) | 이후 (환경변수) |
|---|---|---|---|
| auction | `cloud.elasticsearch.uris` | `http://localhost:9200` | `${ELASTICSEARCH_URI:http://localhost:9200}` |
| product | `cloud.elasticsearch.uris` | `http://localhost:9200` | `${ELASTICSEARCH_URI:http://localhost:9200}` |
| payment | `toss.payments.success-url` | `http://localhost:3000/payments/toss/success` | `${TOSS_PAYMENTS_SUCCESS_URL:http://localhost:3000/payments/toss/success}` |
| payment | `toss.payments.fail-url` | `http://localhost:3000/payments/toss/fail` | `${TOSS_PAYMENTS_FAIL_URL:http://localhost:3000/payments/toss/fail}` |
| payment | `toss.payments.widget-enabled` | `true` | `${TOSS_PAYMENTS_WIDGET_ENABLED:true}` |
| member | `member.email.from-name` | `TodayLunch` | `${MAIL_FROM_NAME:TodayLunch}` |

**로컬 환경**: 기본값이 설정되어 있어 별도 환경변수 없이 기존과 동일하게 동작합니다.

**k8s 배포**: 각 서비스 ConfigMap에 값이 이미 반영되어 있습니다.

---

## 공통 (infra/common)

모든 서비스 Deployment의 `envFrom`에 포함됨.

### ConfigMap (`common-config`)

| 환경변수 | k8s 값 | 로컬 기본값 | 사용 서비스 |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://postgres:5432/goods_mall` | `jdbc:postgresql://localhost:5432/goods_mall` | 전체 |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | `localhost:9092` (또는 `29092`) | 전체 |
| `REDIS_HOST` | `redis` | `localhost` | ai, gateway, member, order |
| `REDIS_PORT` | `6379` | `6379` | ai, gateway, member, order |

### Secret (`common-secret`)

| 환경변수 | 설명 | 사용 서비스 |
|---|---|---|
| `DB_USER_NAME` | PostgreSQL 계정 | 전체 |
| `DB_USER_PASSWORD` | PostgreSQL 비밀번호 | 전체 |

---

## AI 서비스 (port: 8088)

### ConfigMap (`ai-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `AI_EMBEDDING_MODEL` | `text-embedding-3-small` | `text-embedding-3-small` |
| `PROJECT_OPENAI_BASE_URL` | `https://api.openai.com` | `https://api.openai.com` |
| `AI_RECOMMENDATION_RERANK_MODEL` | `gpt-5.4-nano` | `gpt-5.4-nano` |
| `AI_PRODUCT_DRAFT_ASSIST_MODEL` | `gpt-5.4-nano` | `gpt-5.4-nano` |
| `AI_AUCTION_PRICE_RECOMMENDATION_MODEL` | `gpt-5.4-nano` | `gpt-5.4-nano` |

### Secret (`ai-secret`)

| 환경변수 | 설명 |
|---|---|
| `OPENAI_API_KEY` | OpenAI API 키 |

### 기본값으로 동작하는 선택 항목 (ConfigMap 미포함)

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `AI_RECOMMENDATION_RERANK_ENABLED` | `false` | Rerank 기능 활성화 |
| `AI_PRODUCT_DRAFT_ASSIST_ENABLED` | `false` | 상품 초안 보조 활성화 |
| `AI_AUCTION_PRICE_RECOMMENDATION_ENABLED` | `true` | 경매 가격 추천 활성화 |
| `AI_RECOMMENDATION_CACHE_TTL_SECONDS` | `600` | 추천 캐시 TTL |
| `AI_EVENT_IDEMPOTENCY_TTL_SECONDS` | `259200` | 이벤트 중복 방지 TTL |
| `AI_PRODUCT_EVENT_CONSUMER_GROUP` | `ai-product-embedding-group` | Kafka 컨슈머 그룹 |
| `AI_PRODUCT_CREATED_TOPIC` | `product.created` | Kafka 토픽 |
| `AI_PRODUCT_UPDATED_TOPIC` | `product.updated` | Kafka 토픽 |
| `AI_PRODUCT_DELETED_TOPIC` | `product.deleted` | Kafka 토픽 |

---

## Auction 서비스 (port: 8090)

### ConfigMap (`auction-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `AWS_S3_BUCKET` | `todaylunchmenu` | `test-bucket` |
| `ELASTICSEARCH_URI` | `http://elasticsearch:9200` | `http://localhost:9200` |

> **변경 이력**: `cloud.elasticsearch.uris`가 하드코딩(`http://localhost:9200`)에서 `${ELASTICSEARCH_URI:...}` 환경변수 참조로 변경됨.

### Secret (`auction-secret`)

| 환경변수 | 설명 |
|---|---|
| `AWS_ACCESS_KEY` | AWS 액세스 키 |
| `AWS_SECRET_KEY` | AWS 시크릿 키 |

---

## Cart 서비스 (port: 8086)

공통 ConfigMap/Secret만 사용. 별도 설정 없음.

---

## Gateway 서비스 (port: 8080)

### ConfigMap (`gateway-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `GATEWAY_JWT_VALIDATION_ENABLED` | `true` | `true` |

### Secret (`gateway-secret`)

| 환경변수 | 설명 |
|---|---|
| `JWT_SECRET_KEY` | JWT 서명 시크릿 (기본값 없음 — 반드시 설정) |

---

## Member 서비스 (port: 8083)

### ConfigMap (`member-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `API_GATEWAY_HOST` | `http://gateway:8080` | `http://localhost:8080` |
| `AWS_REGION` | `ap-northeast-2` | `ap-northeast-2` |
| `AWS_S3_BUCKET` | `todaylunchmenu` | `todaylunch-local` |
| `EMAIL_PROVIDER` | `smtp` | `logging` |
| `SMTP_HOST` | `sandbox.smtp.mailtrap.io` | `localhost` |
| `SMTP_PORT` | `2525` | `1025` |
| `SMTP_AUTH` | `true` | `false` |
| `SMTP_STARTTLS_ENABLE` | `true` | `false` |
| `MAIL_FROM` | `no-reply@todaylunch.local` | `no-reply@todaylunch.local` |
| `MAIL_FROM_NAME` | `TodayLunch` | `TodayLunch` |
| `EMAIL_VERIFICATION_FRONTEND_URL` | `https://goodsmall.vercel.app/email-verification` | (기본값 없음 — 반드시 설정) |
| `KAKAO_REDIRECT_URI` | `http://3.34.102.179/api/auth/oauth/kakao/callback` | `http://localhost:8083/api/auth/oauth/kakao/callback` |
| `KAKAO_CLIENT_ID` | `40bcc2744d37da5ed008a7a22fe45b7a` | (빈 값) |

> **변경 이력**: `member.email.from-name`이 하드코딩(`TodayLunch`)에서 `${MAIL_FROM_NAME:TodayLunch}` 환경변수 참조로 변경됨.

### Secret (`member-secret`)

| 환경변수 | 설명 |
|---|---|
| `JWT_SECRET_KEY` | JWT 서명 시크릿 |
| `AWS_ACCESS_KEY_ID` | AWS 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | AWS 시크릿 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 OAuth 시크릿 |
| `SMTP_USERNAME` | SMTP 인증 사용자명 |
| `SMTP_PASSWORD` | SMTP 인증 비밀번호 |
| `ACCOUNT_VERIFICATION_SECRET_KEY` | 계정 인증 암호화 키 |

---

## Notification 서비스 (port: 8087)

공통 ConfigMap/Secret만 사용. 별도 설정 없음.

---

## Order 서비스 (port: 8084)

### ConfigMap (`order-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `SERVICES_PAYMENT_URL` | `http://payment:8082` | `http://localhost:8082` |
| `SERVICES_PRODUCT_URL` | `http://product:8081` | `http://localhost:8081` |
| `SWEET_TRACKER_API_BASE_URL` | `https://info.sweettracker.co.kr` | `https://info.sweettracker.co.kr` |

### Secret (`order-secret`)

| 환경변수 | 설명 |
|---|---|
| `SWEET_TRACKER_API_KEY` | 스윗트래커 API 키 |

---

## Payment 서비스 (port: 8082)

### ConfigMap (`payment-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `SERVICES_ORDER_URL` | `http://order:8084` | `http://localhost:8084` |
| `TOSS_PAYMENTS_BASE_URL` | `https://api.tosspayments.com` | `https://api.tosspayments.com` |
| `TOSS_PAYMENTS_CLIENT_KEY` | `test_ck_DLJOpm5QrldyeOMlna9QrPNdxbWn` | `test-client-key` |
| `TOSS_PAYMENTS_SUCCESS_URL` | `https://goodsmall.vercel.app/payments/toss/success` | `http://localhost:3000/payments/toss/success` |
| `TOSS_PAYMENTS_FAIL_URL` | `https://goodsmall.vercel.app/payments/toss/fail` | `http://localhost:3000/payments/toss/fail` |
| `TOSS_PAYMENTS_WIDGET_ENABLED` | `true` | `true` |

> **변경 이력**: `toss.payments.success-url`, `fail-url`, `widget-enabled`가 하드코딩에서 `${TOSS_PAYMENTS_*:...}` 환경변수 참조로 변경됨.

### Secret (`payment-secret`)

| 환경변수 | 설명 |
|---|---|
| `TOSS_PAYMENTS_SECRET_KEY` | Toss Payments 시크릿 키 |
| `PAYMENT_WITHDRAW_CRYPTO_SECRET_KEY` | 출금 암호화 키 |

---

## Product 서비스 (port: 8081)

### ConfigMap (`product-config`)

| 환경변수 | k8s 값 | 로컬 기본값 |
|---|---|---|
| `AWS_S3_BUCKET` | `todaylunchmenu` | `test-bucket` |
| `ELASTICSEARCH_URI` | `http://elasticsearch:9200` | `http://localhost:9200` |

> **변경 이력**: `cloud.elasticsearch.uris`가 하드코딩(`http://localhost:9200`)에서 `${ELASTICSEARCH_URI:...}` 환경변수 참조로 변경됨.

### Secret (`product-secret`)

| 환경변수 | 설명 |
|---|---|
| `AWS_ACCESS_KEY` | AWS 액세스 키 |
| `AWS_SECRET_KEY` | AWS 시크릿 키 |

---

## Settlement 서비스 (port: 8085)

공통 ConfigMap/Secret만 사용. 별도 설정 없음.

---

## 기본값 없는 필수 환경변수 요약

배포 전 반드시 Secret에 실제 값을 채워야 하는 항목들.

| 환경변수 | 서비스 | Secret 파일 |
|---|---|---|
| `DB_USER_NAME` | 전체 | `infra/common/secret.example` |
| `DB_USER_PASSWORD` | 전체 | `infra/common/secret.example` |
| `JWT_SECRET_KEY` | gateway, member | `gateway/secret.example`, `member/secret.example` |
| `OPENAI_API_KEY` | ai | `ai/secret.example` |
| `AWS_ACCESS_KEY` | auction, product | `auction/secret.example`, `product/secret.example` |
| `AWS_SECRET_KEY` | auction, product | `auction/secret.example`, `product/secret.example` |
| `AWS_ACCESS_KEY_ID` | member | `member/secret.example` |
| `AWS_SECRET_ACCESS_KEY` | member | `member/secret.example` |
| `KAKAO_CLIENT_SECRET` | member | `member/secret.example` |
| `SMTP_USERNAME` | member | `member/secret.example` |
| `SMTP_PASSWORD` | member | `member/secret.example` |
| `ACCOUNT_VERIFICATION_SECRET_KEY` | member | `member/secret.example` |
| `EMAIL_VERIFICATION_FRONTEND_URL` | member | `member/configmap.yaml` (이미 설정됨) |
| `TOSS_PAYMENTS_SECRET_KEY` | payment | `payment/secret.example` |
| `PAYMENT_WITHDRAW_CRYPTO_SECRET_KEY` | payment | `payment/secret.example` |
| `SWEET_TRACKER_API_KEY` | order | `order/secret.example` |