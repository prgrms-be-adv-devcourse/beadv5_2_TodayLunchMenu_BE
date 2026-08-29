# 오늘점심메뉴 (TodayLunchMenu) BE

단기심화 5기 2팀의 MSA 기반 점심 메뉴 서비스 백엔드 레포지토리입니다. 상품 판매, 경매, 결제/정산, 알림 등을 담당하는 다수의 독립 서비스로 구성되어 있습니다.

## 기술 스택

- **Language / Framework**: Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.1
- **Build**: Gradle Multi-Module
- **Database**: PostgreSQL (pgvector), Flyway
- **Messaging**: Apache Kafka
- **Cache**: Redis
- **Search**: Elasticsearch
- **Infra / Deploy**: Docker, Kubernetes (k3s), GitHub Actions (CI/CD)
- **Monitoring**: Prometheus, Grafana

## 아키텍처 개요

- **Clean Architecture (Hexagonal)**: `presentation → application → domain ← infrastructure` 계층 구조를 따르며, 비즈니스 로직은 Entity(Fat Domain)에 두고 Service는 오케스트레이션만 담당합니다.
- **서비스 간 통신 원칙**
  - 응답 결과에 따라 다음 로직이 달라지는 경우 → **Feign Client** (동기)
  - 단순 후처리·통지인 경우 → **Kafka** (비동기, fire-and-forget)
- **API 진입점**: `gateway`가 각 서비스로의 라우팅을 정적으로 구성해 단일 진입점 역할을 하며, 인증은 JWT 기반으로 게이트웨이 단에서 처리합니다.

## 모듈 구조

| 모듈 | 포트 | 역할 |
|------|------|------|
| gateway | 8080 | API Gateway, 인증/라우팅, Swagger 통합 |
| product | 8081 | 상품/카테고리/이미지 관리 |
| payment | 8082 | 지갑/결제/에스크로/정산금 적립 |
| member | 8083 | 회원 가입/인증/판매자 등록 |
| order | 8084 | 주문/배송 |
| settlement | 8085 | 판매자 정산 |
| cart | 8086 | 장바구니/찜 |
| notification | 8087 | 알림 저장/조회/실시간 발송(SSE) |
| ai | 8088 | 상품 추천, 상품 등록/경매가 추천 AI |
| auction | 8090 | 실시간 경매 (WebSocket) |
| common-security | - | 공통 인증/보안 라이브러리 |
| common-monitoring | - | 공통 모니터링(Actuator/Prometheus) 라이브러리 |
| db-migration | - | Flyway 기반 스키마 초기화/시드 배치 |

## 실행 방법

### 사전 요구사항
- JDK 21
- Docker / Docker Compose

### 1. 환경 변수 설정
```bash
cp .env.example .env
# 필요한 값(DB, JWT, Kafka, 외부 API 키 등)을 채워 넣는다
```

### 2. 인프라 + 전체 서비스 기동
```bash
docker-compose up -d
```
PostgreSQL, Kafka, Redis, Elasticsearch 등 인프라와 각 서비스가 함께 기동됩니다. 로컬 개발용으로 Prometheus/Grafana까지 포함하려면 `docker-compose.local.yml`을 사용합니다.

### 3. 개별 모듈 로컬 실행 (선택)
```bash
./gradlew :product:bootRun
```

### 기타 Gradle 명령어
```bash
./gradlew build                  # 전체 빌드
./gradlew :product:test          # 특정 모듈 테스트
./gradlew clean build
```

## 모듈별 상세

각 모듈의 도메인 로직과 API 스펙은 하위 모듈 README를 참고하세요.

- [product](product/README.md) — 상품/카테고리/이미지 API
- [cart](cart/README.md) — 장바구니/찜 API
- [member](member/README.md) — 회원 가입/인증/판매자 등록/OAuth
- [order](order/README.md) — 주문/배송 (Kafka 기반 비동기 결제 처리)
- [payment](payment/README.md) — 지갑 충전/카드 결제/에스크로/환불/정산금 적립
- [settlement](settlement/README.md) — 판매자 정산 집계 및 지급
- [notification](notification/README.md) — 알림 저장/조회, SSE 실시간 발송
- [ai](ai/README.md) — pgvector 기반 상품 추천, 상품 등록/경매가 추천 AI
- [db-migration](db-migration/README.md) — Flyway 기반 스키마/시드 데이터 관리
- auction — 실시간 경매 (WebSocket, 동시 입찰 처리). 상세 설계는 [docs/auction-implementation-plan.md](docs/auction-implementation-plan.md) 참고

## API 문서

각 서비스의 Swagger UI는 `gateway`를 통해 통합 제공됩니다.

## CI/CD

- **CI** (`.github/workflows/ci.yml`): PR 생성 시 변경된 모듈만 감지해 `./gradlew :<module>:build` 실행
- **CD** (`.github/workflows/cd.yml`): `main` 브랜치 push 시 변경된 모듈의 Docker 이미지를 빌드해 GHCR에 push하고, self-hosted k3s 클러스터에 `kubectl`로 배포

## 모니터링

Prometheus/Grafana를 통해 서비스 메트릭을 수집·시각화합니다. K8s 환경 설정은 `k8s/monitoring/`, 로컬 환경은 `docker-compose.local.yml`을 참고하세요.

## 디렉토리 구조

```
.
├── product, cart, member, order, payment, settlement, notification, auction, ai  # 도메인 서비스 모듈
├── gateway            # API Gateway
├── common-security    # 공통 보안 라이브러리
├── common-monitoring  # 공통 모니터링 라이브러리
├── db-migration       # 스키마 마이그레이션/시드 배치
├── docs/              # 설계/트러블슈팅 문서
├── k8s/               # Kubernetes 배포 매니페스트
├── monitoring/        # 로컬 Prometheus 설정
└── scripts/           # 개발 편의 스크립트
```
