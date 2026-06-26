# Clov. API 설계

> [Clov_DB_설계.md](Clov_DB_설계.md)의 테이블 구조를 기준으로 작성한 REST API 명세다. 백엔드 스택(Spring Boot 3.5, Java 21, Spring Security + OAuth2 Client, MyBatis, MySQL)에 맞춰 설계했다.

## 1. 권한 모델 — "방장 없음"을 API 레벨에서 어떻게 지킬 것인가

DB 설계에서 `FRIENDSHIP_ROOMS`/`ROOM_MEMBERS`에 역할·대표자 필드를 두지 않은 것처럼, API의 인가(authorization) 로직도 역할 기반(role-based)이 아니라 **두 단계 규칙**으로만 판단한다.

1. **공간 멤버십 검사**: 요청자가 해당 `room_id`의 `ROOM_MEMBERS`에 `status=ACTIVE`로 존재하는가? 아니면 모든 조회/작성 요청을 차단한다. (공간 안에서는 전원이 같은 권한)
2. **작성자 본인 검사**: 수정/삭제는 그 row의 `writer_id`/`sender_id`(작성자 본인)인 경우에만 허용한다. 역할이 높아서 남의 글을 지울 수 있는 경로는 만들지 않는다.

이 두 규칙만으로 모든 도메인의 인가를 처리하며, "관리자/방장이 멤버를 강퇴한다" 같은 API는 의도적으로 만들지 않는다(나가기는 본인만 가능).

## 2. 인증 방식

- 소셜 로그인(OAuth2) 기반 가입·로그인 (기획서 9장 리스크 관리와 일치)
- 로그인 성공 후 서버가 자체 Access Token(JWT)을 발급, 이후 요청은 `Authorization: Bearer <token>` 헤더로 인증
- 모든 도메인 API는 인증 필요. 인증 관련 API만 예외

| Method | Path | 설명 |
|---|---|---|
| GET | `/oauth2/authorization/{provider}` | 소셜 로그인 시작 (Spring Security 기본 제공) |
| GET | `/login/oauth2/code/{provider}` | 소셜 로그인 콜백, 신규 유저면 USERS row 생성 후 토큰 발급 |
| POST | `/api/v1/auth/refresh` | 리프레시 토큰으로 Access Token 재발급 |
| POST | `/api/v1/auth/logout` | 로그아웃(토큰 무효화) |

## 3. 공통 규칙

- Base path: `/api/v1`
- 요청/응답: JSON, 날짜는 ISO-8601(`yyyy-MM-dd`, `HH:mm:ss`)
- 목록 조회는 페이지네이션 공통 파라미터 사용: `page`(0부터), `size`(기본 20)
- 에러 응답 포맷

```json
{
  "code": "ROOM_MEMBER_NOT_FOUND",
  "message": "해당 우정공간의 멤버가 아닙니다.",
  "status": 403
}
```

- 주요 에러 코드: `ROOM_MEMBER_NOT_FOUND`(403, 멤버십 검사 실패), `NOT_WRITER`(403, 작성자 본인 아님), `INVITE_EXPIRED`(409), `INVITE_ALREADY_USED`(409), `PLAN_NOT_COMPLETED`(409, 완료 전 추억 작성 시도)

## 4. 도메인별 엔드포인트

### 4-1. Users

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| GET | `/api/v1/users/me` | 내 프로필(닉네임, 개인 초대 코드 등) 조회 | 로그인 본인 |
| PATCH | `/api/v1/users/me` | 닉네임 등 프로필 수정 | 로그인 본인 |
| GET | `/api/v1/users/me/rooms` | 내가 `ACTIVE`로 속한 우정공간 목록 | 로그인 본인 |

### 4-2. Friendship Rooms

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/rooms` | 우정공간 생성(생성자는 자동으로 첫 `ROOM_MEMBERS` row, 일반 멤버와 동일) | 로그인 사용자 |
| GET | `/api/v1/rooms/{roomId}` | 우정공간 상세(이름, 레벨, exp, 멤버 수 등) | 공간 멤버 |
| PATCH | `/api/v1/rooms/{roomId}` | 우정공간 이름 등 수정 | 공간 멤버(누구나) |
| GET | `/api/v1/rooms/{roomId}/members` | 멤버 목록(`ACTIVE`/`LEFT` 포함, 기록 보존 확인용) | 공간 멤버 |
| DELETE | `/api/v1/rooms/{roomId}/members/me` | 내가 이 공간에서 나가기 (row 삭제 아님, `status=LEFT`, `left_at` 기록) | 공간 멤버 본인 |

- 멤버 전원이 `LEFT`가 되면 서버가 `FRIENDSHIP_ROOMS.status`를 `INACTIVE`로 자동 전환한다(별도 "방 삭제" API는 없음 — 방장이 없으므로 누구도 방을 강제 종료할 권한을 갖지 않는다).
- `ARCHIVED` 전환은 사용자 액션이 아니라 운영 정책(예: 비활성 N개월 경과)으로만 이뤄지며 1차 MVP 범위 밖이다.

### 4-3. Room Invites

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/invites` | 초대 코드 생성(`created_by`=요청자, 단순 이력) | 공간 멤버(누구나) |
| GET | `/api/v1/rooms/{roomId}/invites` | 이 공간에서 발급된 초대 코드 목록 | 공간 멤버 |
| DELETE | `/api/v1/invites/{inviteId}` | 초대 코드 취소(`status=CANCELED`) | 코드를 만든 사용자 본인만 |
| POST | `/api/v1/invites/accept` | 초대 코드(`invite_code`)로 공간 참여 → `ROOM_MEMBERS` row 생성, 코드 `status=USED` | 로그인 사용자(아직 멤버 아닌 경우만) |

`invites/accept`는 인원 수 제한을 두지 않으므로, "공간에 몇 명이 있든" 그대로 동작한다(1:1 전용 분기 없음).

### 4-4. Plans (약속)

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/plans` | 약속 등록(`status=SCHEDULED`, `memory_status=NONE`) | 공간 멤버 |
| GET | `/api/v1/rooms/{roomId}/plans` | 약속 목록(필터: `status`, 날짜 범위) | 공간 멤버 |
| GET | `/api/v1/plans/{planId}` | 약속 상세(체크리스트 포함) | 공간 멤버 |
| PATCH | `/api/v1/plans/{planId}` | 약속 내용 수정 | 작성자 본인 |
| DELETE | `/api/v1/plans/{planId}` | 약속 삭제 | 작성자 본인 |
| POST | `/api/v1/plans/{planId}/complete` | 약속 완료 처리 → `status=COMPLETED`, `completed_at` 기록, `memory_status=CANDIDATE`, `memory_candidate_created_at` 기록 | 공간 멤버(누구나 — 만남에 다녀온 사람이 처리) |
| POST | `/api/v1/plans/{planId}/cancel` | 약속 취소 → `status=CANCELED` | 작성자 본인 |
| POST | `/api/v1/plans/{planId}/skip-memory` | 추억 작성 안 함 처리 → `memory_status=SKIPPED` | 공간 멤버 |

`complete`를 작성자 전용이 아니라 "공간 멤버 누구나"로 둔 이유: 약속을 등록한 사람과 실제로 다녀와서 완료 처리하는 사람이 다를 수 있고, 방장이 없으므로 한 사람만 이 권한을 갖는 구조를 만들지 않는다.

### 4-5. Plan Checklists

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/plans/{planId}/checklists` | 체크리스트 항목 추가 | 공간 멤버 |
| PATCH | `/api/v1/checklists/{checklistId}` | 내용 수정 또는 `checked` 토글 | 공간 멤버(체크리스트는 공동 준비물이라 작성자 본인 제한을 두지 않음) |
| DELETE | `/api/v1/checklists/{checklistId}` | 항목 삭제 | 공간 멤버 |

### 4-6. Memories (추억)

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/plans/{planId}/memories` | 해당 약속에 대한 내 추억 작성(`writer_id`=요청자) → 성공 시 해당 plan의 `memory_status=WRITTEN` | 공간 멤버. `memory_status=CANDIDATE` 또는 `WRITTEN` 상태에서만 허용(`NONE`이면 `PLAN_NOT_COMPLETED`) |
| GET | `/api/v1/rooms/{roomId}/memories` | 공간의 추억 피드(월별 필터, `writer_id` 필터로 "내 기록/친구 기록" 구분) | 공간 멤버 |
| GET | `/api/v1/memories/{memoryId}` | 추억 상세(이미지 포함) | 공간 멤버 |
| PATCH | `/api/v1/memories/{memoryId}` | 내용 수정 | 작성자 본인 |
| DELETE | `/api/v1/memories/{memoryId}` | 삭제 | 작성자 본인 |

같은 `plan_id`에 여러 멤버가 각각 `POST`를 호출하면 `writer_id`가 다른 여러 `MEMORIES` row가 생기는 구조 그대로, "같은 약속에 대한 친구별 다른 시점 기록"이 자연스럽게 구현된다(2차 강화 차별점).

### 4-7. Memory Images

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/memories/{memoryId}/images` | 이미지 업로드(`sort_order` 지정) | 추억 작성자 본인 |
| DELETE | `/api/v1/memory-images/{imageId}` | 이미지 삭제 | 추억 작성자 본인 |
| PATCH | `/api/v1/memories/{memoryId}/images/order` | 이미지 순서 재정렬(`sort_order` 일괄 업데이트) | 추억 작성자 본인 |

### 4-8. Lucky Letters (행운편지)

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| POST | `/api/v1/rooms/{roomId}/letters` | 편지 작성·발송(`sender_id`=요청자, `receiver_id` 지정) | 공간 멤버(receiver도 같은 공간 멤버여야 함) |
| GET | `/api/v1/rooms/{roomId}/letters?box=received` | 받은 편지함 | 공간 멤버(본인이 `receiver_id`인 것만) |
| GET | `/api/v1/rooms/{roomId}/letters?box=sent` | 보낸 편지함 | 공간 멤버(본인이 `sender_id`인 것만) |
| PATCH | `/api/v1/letters/{letterId}/read` | 읽음 처리(`read_at` 기록) | 수신자 본인 |
| PATCH | `/api/v1/letters/{letterId}/favorite` | 즐겨찾기 토글(`is_favorite`) | 발신자 또는 수신자 본인 |

### 4-9. Friendship Exp Logs (우정 레벨/경험치)

| Method | Path | 설명 | 인가 |
|---|---|---|---|
| GET | `/api/v1/rooms/{roomId}/exp-logs` | 경험치 변화 이력(누가 어떤 활동으로 얼마나 적립했는지) | 공간 멤버 |
| GET | `/api/v1/rooms/{roomId}/level` | 현재 `friendship_level`, `exp_point`, 다음 레벨까지 필요한 exp | 공간 멤버 |

`FRIENDSHIP_EXP_LOGS`에 직접 쓰는 API는 없다. 경험치는 사용자가 호출하는 게 아니라, 약속 완료(`/plans/{id}/complete`)·추억 작성(`/memories`)·편지 발송(`/letters`) 같은 액션이 서버 내부에서 부수효과로 적립한다(`triggered_by`=그 액션을 한 사용자). 클라이언트가 직접 exp를 조작할 수 있는 경로를 만들지 않는다.

## 5. 서비스 철학 ↔ API 설계 대응

| 서비스 철학 | API 설계 반영 |
|---|---|
| 방장 없음 | "방 삭제", "멤버 강퇴" API 자체가 없음. 모든 쓰기 API는 공간 멤버십만 검사 |
| 초대자도 대표자 아님 | `POST /invites`는 누구나 호출 가능, `created_by`는 응답에 노출은 하되 권한 분기에 쓰지 않음 |
| 한 명이 남아도 기록 보존 | `DELETE /rooms/{id}/members/me`는 row를 지우지 않고 `status=LEFT`. 이후 그 사람이 쓴 PLANS/MEMORIES/LETTERS는 그대로 조회 가능 |
| 약속 → 추억 전환 | `POST /plans/{id}/complete`가 `memory_status`를 `CANDIDATE`로 바꾸는 유일한 트리거. `POST /memories`는 `CANDIDATE` 상태가 아니면 막힘 |
| 친구별 다른 시점 기록 | `POST /plans/{id}/memories`를 멤버별로 각각 호출 가능, `writer_id`로 row 분리 |
| 인원 제한 없는 친구방 | `invites/accept`, `members` 목록 어디에도 인원수 상한 로직 없음 |
