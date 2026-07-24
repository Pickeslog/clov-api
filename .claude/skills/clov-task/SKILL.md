---
name: clov-task
description: Clov 백엔드(clov-api) 이슈를 처음부터 끝까지 처리하는 절차. GitHub 이슈 번호를 받아 작업을 시작할 때, 브랜치를 만들 때, PR을 올리기 전에 사용한다. 도메인 API 추가·버그 수정·마이그레이션 등 clov-api의 모든 작업에 적용된다.
---

# Clov 백엔드 작업 절차

이 문서는 **팀원이 이슈 하나를 맡아 머지까지 가는 전 과정**을 정한다.

## 1. 시작 전에 읽는다

1. **이슈 본문 전체** — 특히 **"범위 밖 (하지 말 것)"** 항목
2. `AGENTS.md` — 이 저장소의 규칙
3. `docs/API-CONTRACT.md` — **API 계약이 정본이다.** 엔드포인트·요청/응답 필드·에러 코드를 여기서 확인한다. 계약에 없는 필드를 마음대로 추가하지 않는다
4. `../web-design-repository/api-spec/05-db-unified-final.md` — DB 스키마 정본

계약과 다르게 만들어야 할 이유가 생기면 **코드를 먼저 고치지 말고 이슈에 댓글로 묻는다.** 계약 변경은 리더 승인 사항이다.

## 2. 브랜치

```
git checkout main
git pull origin main
git checkout -b feat/<이슈번호>-<작업이름>
```

`main`에서 직접 작업하지 않는다. `git pull`을 건너뛰지 않는다.

## 3. 코드 규칙

### 반드시 지킬 것

| 규칙 | 이유 |
|---|---|
| **INSERT 후에는 `findById`로 다시 조회해서 반환** | Java 객체를 그대로 반환하면 DB 기본값(`status`, `created_at` 등)이 `null`인 채로 응답에 나간다. **실제로 터졌던 버그다** |
| 도메인 간 공유 DTO는 `global/dto`에 | `domain` 아래에 같은 이름 클래스가 둘 생기면 MyBatis 별칭이 충돌해 **애플리케이션 전체가 부팅 실패**한다. 실제로 main이 붕괴한 적 있다 |
| 새 도메인을 만들면 `application.yaml`의 `type-aliases-package`에 그 도메인의 `.entity`를 추가 | 위 사고의 재발 방지책 |
| 멤버십 검사는 `RoomService.assertActiveMember` 재사용 | 도메인마다 따로 만들지 않는다 |
| 시각은 `LocalDateTime.now(ZoneOffset.UTC)` | 앱 전체가 UTC 기준이다 |
| ID는 응답에서 **문자열** | 계약 §2 공통 규약 |

### 하지 말 것

- **방장·관리자·권한 개념 도입** — 우정공간의 모든 멤버는 동등하다. 서비스 핵심 원칙이라 예외 없다
- 계약에 없는 엔드포인트·필드를 임의로 추가
- 이슈 "범위 밖" 파일 수정

## 4. 테스트

**통합테스트 없이 PR을 올리지 않는다.** 이 저장소는 Testcontainers로 진짜 MySQL을 띄워 검증한다.

- 위치: `src/test/java/.../<도메인>IntegrationTest.java`
- 기존 테스트(`RoomIntegrationTest`, `MemoryIntegrationTest`)를 그대로 흉내 낸다
- 성공 경로만이 아니라 **권한 없음·잘못된 입력·중복** 같은 실패 경로도 확인한다

### ⚠️ 새로운 부수효과를 추가했다면

내 변경이 **다른 테이블에 행을 새로 쓴다면**(로그 적립, 알림 생성 등), 그 도메인을 쓰는 **기존 테스트들의 `@AfterEach` 정리 코드**를 반드시 확인한다.

정리에서 그 테이블을 안 지우면 `DELETE FROM ...`가 외래키 제약에 걸려 **내 코드와 무관한 테스트가 무더기로 깨진다.** 실제로 이것 때문에 CI가 13건 실패한 적 있다.

## 5. 로컬 검증

```
./gradlew compileJava compileTestJava
```

로컬에 Docker가 없으면 **통합테스트는 돌지 않는다.** 컴파일까지만 확인하고 나머지는 CI에 맡긴다. CI가 진짜 DB로 검증해준다.

## 6. PR

- PR 본문 **첫 줄에 `Closes #<이슈번호>`**
- 제목은 `feat: 무엇을 했는지 (#이슈번호)` 형식
- 본문에는 **무엇을 · 왜 · 어떻게 검증했는지**
- **CI 초록불 확인 후** 리뷰 요청. 머지는 리더가 한다

## 7. 머지된 다음 — 서버 재기동

백엔드는 프론트와 달리 **자동으로 반영되지 않는다.** 머지 후 8080이 옛 빌드면 계속 옛날 동작을 한다.

```
git pull
./gradlew clean bootRun
```

"분명 고쳤는데 그대로다" 싶으면 **십중팔구 옛 빌드가 떠 있는 것이다.** 코드를 파헤치기 전에 재기동부터 확인한다.

## 8. 막혔을 때

혼자 30분 이상 헤매지 않는다. 이슈에 댓글로 — 무엇을 하려 했는지, 무슨 에러가 났는지(메시지 그대로), 뭘 시도했는지.

## 참고 — 골든 레퍼런스

- 도메인 한 벌(컨트롤러·서비스·매퍼·XML·DTO): `domain/room/`
- 통합테스트: `src/test/java/.../room/RoomIntegrationTest.java`
- 공유 DTO: `global/dto/UserSummaryResponse.java`
