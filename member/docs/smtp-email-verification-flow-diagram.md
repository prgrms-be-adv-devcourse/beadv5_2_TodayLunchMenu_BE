# SMTP Email Verification Flow Diagram

## 개요

이 문서는 `LoggingEmailSender` 대신 실제 `SmtpEmailSender`가 적용되었을 때,
회원가입부터 이메일 인증 완료까지의 흐름을 mermaid 다이어그램으로 설명한다.

주요 구성 요소:

- FE Signup / Login / Verification Pages
- Gateway
- member-service
- EmailVerificationService
- SmtpEmailSender
- 외부 SMTP provider
- 사용자 메일함

## 1. 회원가입 후 인증 메일 발송 흐름

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as Frontend
    participant GW as Gateway
    participant MS as member-service
    participant MBR as MemberService
    participant EVS as EmailVerificationService
    participant DB as Member DB
    participant SMTP as SmtpEmailSender
    participant Provider as SMTP Provider
    participant Mailbox as User Mailbox

    User->>FE: 회원가입 정보 입력
    FE->>GW: POST /api/auth
    GW->>MS: 회원가입 요청 전달
    MS->>MBR: createMember()
    MBR->>DB: Member 저장(status=PENDING_VERIFICATION)
    MBR->>EVS: createSignupVerification(member)
    EVS->>DB: EmailVerification 저장(status=PENDING)
    EVS->>SMTP: send(to, subject, body)
    SMTP->>Provider: SMTP 전송
    Provider->>Mailbox: 인증 메일 전달
    EVS-->>MBR: verification 생성 완료
    MBR-->>MS: 회원가입 완료
    MS-->>GW: 201 Created
    GW-->>FE: 회원가입 성공 + PENDING_VERIFICATION
    FE-->>User: 인증 대기 화면 표시
```

## 2. 인증 메일 재발송 흐름

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as Pending Verification Page
    participant GW as Gateway
    participant MS as member-service
    participant EVS as EmailVerificationService
    participant DB as Member DB
    participant SMTP as SmtpEmailSender
    participant Provider as SMTP Provider
    participant Mailbox as User Mailbox

    User->>FE: 인증 메일 다시 보내기 클릭
    FE->>GW: POST /api/auth/email-verifications
    GW->>MS: 재발송 요청 전달
    MS->>EVS: resendSignupVerification(email)
    EVS->>DB: 기존 PENDING 토큰 무효화(CANCELLED)
    EVS->>DB: 새 EmailVerification 저장(PENDING)
    EVS->>SMTP: send(to, subject, body)
    SMTP->>Provider: SMTP 전송
    Provider->>Mailbox: 새 인증 메일 전달
    MS-->>GW: 재발송 성공
    GW-->>FE: 200 OK
    FE-->>User: 재발송 완료 메시지 표시
```

## 3. 이메일 인증 확인 흐름

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Mailbox as User Mailbox
    participant FE as EmailVerificationPage
    participant GW as Gateway
    participant MS as member-service
    participant EVS as EmailVerificationService
    participant DB as Member DB

    Mailbox-->>User: 인증 링크 수신
    User->>FE: /email-verification?token=... 접속
    FE->>GW: POST /api/auth/email-verifications/confirm
    GW->>MS: 인증 확인 요청 전달
    MS->>EVS: confirmSignupVerification(token)
    EVS->>DB: 토큰 조회 및 상태 검증

    alt 토큰 유효 + 만료 전 + 회원 상태 PENDING_VERIFICATION
        EVS->>DB: EmailVerification.status = VERIFIED
        EVS->>DB: Member.status = ACTIVE
        EVS-->>MS: 인증 성공
        MS-->>GW: 200 OK
        GW-->>FE: 인증 완료 응답
        FE-->>User: 인증 완료 및 로그인 유도
    else 토큰 만료
        EVS-->>MS: EMAIL_VERIFICATION_TOKEN_EXPIRED
        MS-->>GW: 410 Gone
        GW-->>FE: 만료 응답
        FE-->>User: 재발송 유도
    else 토큰 무효
        EVS-->>MS: EMAIL_VERIFICATION_TOKEN_INVALID
        MS-->>GW: 400 Bad Request
        GW-->>FE: 무효 응답
        FE-->>User: 최신 메일 확인 안내
    else 현재 상태상 인증 불가
        EVS-->>MS: EMAIL_VERIFICATION_NOT_ALLOWED
        MS-->>GW: 409 Conflict
        GW-->>FE: 인증 불가 응답
        FE-->>User: 로그인 또는 재가입 유도
    end
```

## 4. 로그인 시 미인증 회원 차단 흐름

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as LoginPage
    participant GW as Gateway
    participant MS as member-service
    participant AS as AuthService
    participant DB as Member DB

    User->>FE: 이메일/비밀번호 입력
    FE->>GW: POST /api/auth/login
    GW->>MS: 로그인 요청 전달
    MS->>AS: authenticate()
    AS->>DB: 회원 조회

    alt Member.status == ACTIVE
        AS-->>MS: 로그인 성공
        MS-->>GW: 토큰 발급
        GW-->>FE: 200 OK
        FE-->>User: 로그인 성공
    else Member.status == PENDING_VERIFICATION
        AS-->>MS: EMAIL_VERIFICATION_REQUIRED
        MS-->>GW: 403 Forbidden
        GW-->>FE: 미인증 응답
        FE-->>User: 인증 후 로그인 안내 + 재발송 버튼 표시
    else 기타 비정상 상태
        AS-->>MS: 로그인 거부
        MS-->>GW: 적절한 에러 응답
        GW-->>FE: 실패 응답
        FE-->>User: 상태별 메시지 표시
    end
```

## 5. 회원 상태 전이

```mermaid
stateDiagram-v2
    [*] --> PENDING_VERIFICATION: 회원가입 완료
    PENDING_VERIFICATION --> ACTIVE: 이메일 인증 성공
    PENDING_VERIFICATION --> DELETED: 가입 취소/정리
    ACTIVE --> SUSPENDED: 운영 제재
    SUSPENDED --> ACTIVE: 제재 해제
    ACTIVE --> WITHDRAWN: 회원 탈퇴
    WITHDRAWN --> DELETED: 개인정보 정리
```

## 6. EmailVerification 상태 전이

```mermaid
stateDiagram-v2
    [*] --> PENDING: 토큰 생성
    PENDING --> VERIFIED: 인증 성공
    PENDING --> CANCELLED: 재발송으로 이전 토큰 무효화
    PENDING --> EXPIRED: 만료 시간 경과
```

## 7. SMTP 적용 시 추가 관찰 포인트

- `EmailVerificationService`는 토큰 생성과 상태 변경의 중심이다.
- 실제 메일 발송 책임은 `SmtpEmailSender`가 가진다.
- SMTP provider 장애가 발생하면 회원가입/재발송 요청도 실패 처리될 수 있다.
- local 환경은 `LoggingEmailSender`, staging/prod는 `SmtpEmailSender`로 분기하는 구성이 적합하다.
- FE는 이제 문자열이 아니라 `EMAIL_VERIFICATION_REQUIRED`, `EMAIL_VERIFICATION_TOKEN_EXPIRED` 같은 전용 코드로 UX를 분기한다.
