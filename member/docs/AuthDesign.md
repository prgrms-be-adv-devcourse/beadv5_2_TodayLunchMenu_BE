# Member Auth Design

## 목적
- 회원가입, 로그인, 토큰 재발급, 로그아웃을 처리한다.
- 이메일 인증을 통해 `PENDING_VERIFICATION` 회원을 `ACTIVE` 로 전환한다.
- 액세스 토큰은 게이트웨이와 각 서비스의 인증 컨텍스트 생성에 사용한다.
- 리프레시 토큰은 Redis 에 저장하여 재발급과 로그아웃을 제어한다.

## 구성 요소
| 컴포넌트 | 역할 |
| --- | --- |
| `AuthController` | 인증 API 진입점 |
| `AuthService` | 로그인, 토큰 재발급, 로그아웃 처리 |
| `MemberService` | 회원 생성 및 회원 정보 관리 |
| `EmailVerificationService` | 이메일 인증 생성, 재발송, 확인 처리 |
| `JwtTokenProvider` | 액세스/리프레시 토큰 생성 및 검증 |
| `RedisRefreshTokenStore` | 회원별 리프레시 토큰 저장 |
| `MemberRestrictionService` | 로그인 제한 제재 여부 확인 |
| `EmailSender` | 이메일 발송 추상화 |

## 회원가입 흐름
1. 클라이언트가 `POST /api/auth` 호출
2. `MemberService` 가 회원 생성
3. 회원 기본 상태는 `PENDING_VERIFICATION`
4. `EmailVerificationService` 가 가입용 인증 토큰 생성
5. `EmailSender` 가 인증 메일 발송
6. `MemberSignedUpEvent` 발행
7. 회원가입 응답 반환

## 이메일 인증 흐름
1. 사용자가 메일 링크 클릭
2. 프론트가 `POST /api/auth/email-verifications/confirm` 호출
3. `EmailVerificationService` 가 토큰 조회
4. 토큰 만료/취소/유효 여부 확인
5. 성공 시 `EmailVerification.status = VERIFIED`
6. `Member.status = ACTIVE`
7. 인증 완료 응답 반환

## 이메일 인증 재발송 흐름
1. 클라이언트가 `POST /api/auth/email-verifications` 호출
2. 이메일로 회원 조회
3. 기존 `PENDING` 인증 요청은 `CANCELLED` 처리
4. 새 토큰 생성 및 저장
5. 인증 메일 재발송

## 로그인 흐름
1. 클라이언트가 `POST /api/auth/login` 호출
2. `AuthService` 가 이메일로 회원 조회
3. 비밀번호 비교 실패 시 `InvalidLoginException`
4. 회원 상태가 `ACTIVE` 가 아니면 로그인 차단
5. `MemberRestrictionService` 로 `LOGIN_BAN` 제재 여부 확인
6. 액세스 토큰, 리프레시 토큰 발급
7. 리프레시 토큰을 Redis 에 저장
8. 토큰 정보 응답 반환

## 토큰 재발급 흐름
1. 클라이언트가 `POST /api/auth/refresh` 호출
2. 리프레시 토큰 형식과 서명 검증
3. 토큰에서 `memberId` 추출
4. 회원 상태가 `ACTIVE` 인지 확인
5. 로그인 제재 여부 확인
6. Redis 저장 토큰과 요청 토큰 일치 여부 확인
7. 새 액세스 토큰 발급
8. 기존 리프레시 토큰은 그대로 유지

## 로그아웃
- `logout(memberId)` 호출 시 Redis 의 리프레시 토큰을 제거한다.
- 현재 구현은 액세스 토큰 블랙리스트를 관리하지 않는다.

## 설계 메모
- 게이트웨이는 JWT 유효성 검증을 수행한다.
- 회원 상태 검증 책임은 member-service 가 가진다.
- 이메일 인증 링크는 프론트 URL 로 연결하고, 프론트가 confirm API 를 호출하는 구조를 권장한다.
