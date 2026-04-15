# Auth API Spec

## 엔드포인트
| Method | Endpoint | 설명 | 요청 시 필요한 데이터 | 구현 여부 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth` | 회원 가입 | Body: `email`, `password`, `nickname`, `phone`, `address`, `profileImageKey`, `role` | 완료 |
| `POST` | `/api/auth/login` | 로그인 | Body: `email`, `password` | 완료 |
| `POST` | `/api/auth/refresh` | 액세스 토큰 재발급 | Body: `refreshToken` | 완료 |
| `POST` | `/api/auth/logout/{memberId}` | 로그아웃 | Path: `memberId` | 완료 |
| `POST` | `/api/auth/email-verifications` | 이메일 인증 재발송 | Body: `email` | 완료 |
| `POST` | `/api/auth/email-verifications/confirm` | 이메일 인증 확인 | Body: `token` | 완료 |

## 공통 응답 형식
```json
{
  "success": true,
  "data": {},
  "error": null
}
```

## 1. 회원 가입
### `POST /api/auth`

#### 요청 시 필요한 데이터
| 위치 | 필드 | 타입 | 필수 |
| --- | --- | --- | --- |
| Body | `email` | String | Y |
| Body | `password` | String | Y |
| Body | `nickname` | String | Y |
| Body | `phone` | String | N |
| Body | `address` | String | N |
| Body | `profileImageKey` | String | N |
| Body | `role` | String | N |

#### Response JSON
```json
{
  "success": true,
  "data": {
    "memberId": "11111111-1111-1111-1111-111111111111",
    "email": "user@test.local",
    "nickname": "점심유저",
    "phone": "010-1234-5678",
    "address": "서울시 강남구",
    "profileImageUrl": "https://cdn.example.com/profiles/2026/04/user.png",
    "role": "USER",
    "status": "PENDING_VERIFICATION",
    "createdAt": "2026-04-10T11:00:00",
    "updatedAt": "2026-04-10T11:00:00"
  },
  "error": null
}
```

## 2. 로그인
### `POST /api/auth/login`

#### 비고
- `ACTIVE` 상태 회원만 로그인 가능하다.
- `PENDING_VERIFICATION` 상태 회원은 로그인할 수 없다.

## 3. 토큰 재발급
### `POST /api/auth/refresh`

#### 비고
- `ACTIVE` 상태 회원만 refresh token 재발급 가능하다.

## 4. 로그아웃
### `POST /api/auth/logout/{memberId}`

## 5. 이메일 인증 재발송
### `POST /api/auth/email-verifications`

#### 요청 시 필요한 데이터
| 위치 | 필드 | 타입 | 필수 |
| --- | --- | --- | --- |
| Body | `email` | String | Y |

#### Request JSON
```json
{
  "email": "user@test.local"
}
```

#### Response JSON
```json
{
  "success": true,
  "data": {
    "email": "user@test.local",
    "purpose": "SIGNUP",
    "status": "PENDING",
    "expiresAt": "2026-04-14T11:00:00"
  },
  "error": null
}
```

## 6. 이메일 인증 확인
### `POST /api/auth/email-verifications/confirm`

#### 요청 시 필요한 데이터
| 위치 | 필드 | 타입 | 필수 |
| --- | --- | --- | --- |
| Body | `token` | String | Y |

#### Request JSON
```json
{
  "token": "random-verification-token"
}
```

#### Response JSON
```json
{
  "success": true,
  "data": {
    "memberId": "11111111-1111-1111-1111-111111111111",
    "email": "user@test.local",
    "status": "ACTIVE"
  },
  "error": null
}
```
