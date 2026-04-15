# Member Enum Spec

## Member Enum

### `MemberStatus`

| 값                     | 설명                                           |
| ---------------------- | ---------------------------------------------- |
| `PENDING_VERIFICATION` | 이메일 인증 전인 회원                          |
| `ACTIVE`               | 정상 활동 가능 회원                            |
| `SUSPENDED`            | 운영 정책에 의해 이용이 정지된 회원            |
| `WITHDRAWN`            | 회원 탈퇴가 확정된 회원                        |
| `DELETED`              | 개인정보 삭제 또는 비식별 처리까지 완료된 회원 |

### `EmailVerificationPurpose`

| 값       | 설명                                       |
| -------- | ------------------------------------------ |
| `SIGNUP` | 회원가입 후 계정 활성화를 위한 이메일 인증 |

### `EmailVerificationStatus`

| 값          | 설명                                    |
| ----------- | --------------------------------------- |
| `PENDING`   | 발송되었고 아직 확인되지 않은 상태      |
| `VERIFIED`  | 이메일 인증이 완료된 상태               |
| `EXPIRED`   | 만료 시간이 지나 사용할 수 없는 상태    |
| `CANCELLED` | 재발송 등으로 기존 토큰이 무효화된 상태 |

### `ReportStatus`

| 값         | 설명           |
| ---------- | -------------- |
| `PENDING`  | 검토 대기      |
| `APPROVED` | 신고 승인 완료 |
| `REJECTED` | 신고 반려 완료 |

### `ReportType`

| 값      | 설명                 |
| ------- | -------------------- |
| `ABUSE` | 욕설, 괴롭힘 등      |
| `FRAUD` | 사기, 허위 거래 등   |
| `SPAM`  | 스팸, 반복적 홍보 등 |
| `ETC`   | 기타 사유            |

### `RestrictionType`

| 값          | 설명           |
| ----------- | -------------- |
| `TRADE_BAN` | 거래 기능 제한 |
| `LOGIN_BAN` | 로그인 제한    |
| `CHAT_BAN`  | 채팅 제한      |

## External Shared Enum

### `MemberRole`

공통 보안 모듈에 정의되어 있으며 member 모듈에서도 사용한다.

| 값       | 설명                  |
| -------- | --------------------- |
| `USER`   | 일반 회원             |
| `SELLER` | 판매자 권한 보유 회원 |
| `ADMIN`  | 관리자                |

## Usage

- `Member.status` -> `MemberStatus`
- `Member.role` -> `MemberRole`
- `EmailVerification.purpose` -> `EmailVerificationPurpose`
- `EmailVerification.status` -> `EmailVerificationStatus`
- `MemberReport.reportType`, `CreateMemberReportRequest.reportType` -> `ReportType`
- `MemberReport.status` -> `ReportStatus`
- `MemberRestriction.restrictionType`, `ReviewMemberReportRequest.restrictionType` -> `RestrictionType`
