# Clov. API

> Clov 프로젝트의 백엔드 REST API 저장소입니다. 친구와의 약속·추억·행운편지·우정 레벨을 관리합니다.

## 🛠 기술 스택

- **언어/프레임워크**: Java 21, Spring Boot 4.0.x
- **인증**: Spring Security 6 + JWT(jjwt 0.12.x) — 이메일/비밀번호 로그인 및 소셜 로그인(Google, Naver, Kakao) 병행
- **데이터 접근**: MyBatis(`mybatis-spring-boot-starter` 4.0.1) — JPA·`@Entity`·Spring Data는 사용하지 않습니다
- **DB**: MySQL 8
- **스토리지**: Cloudflare R2(S3 호환, AWS SDK v2)
- **문서화**: springdoc-openapi (Swagger UI)
- **테스트**: JUnit 5, Testcontainers(MySQL)
- **빌드**: Gradle

## 🔐 인증·인가 모델

역할(방장/관리자) 개념 없이 두 단계 규칙으로만 인가를 판단합니다.

1. **공간 멤버십 검사** — 요청자가 해당 `room_id`의 `room_members`에 `status=ACTIVE`로 있는지 확인. 아니면 차단.
2. **작성자 본인 검사** — 수정/삭제는 그 row의 `writer_id`/`sender_id` 본인만 허용.

## 📦 공통 응답 포맷

```jsonc
// 성공
{ "success": true, "data": { /* payload */ } }
// 실패
{ "success": false, "error": { "code": "ROOM_MEMBER_NOT_FOUND", "message": "해당 우정공간의 멤버가 아닙니다." } }
```

## 📁 프로젝트 구조

도메인 단위 패키지 구조를 사용합니다. (MyBatis, JPA 미사용)

```
com/korit/clovapi/
├── domain/
│   ├── auth/            # 회원가입·로그인·OAuth2·비밀번호 재설정
│   ├── user/             # 프로필·환경설정
│   ├── room/             # 우정공간·멤버·마스코트·경험치
│   ├── invite/            # 초대 코드·가입 신청·승인
│   ├── plan/               # 약속·체크리스트·4단계 인증사진
│   ├── memory/          # 추억·이미지·댓글
│   ├── letter/             # 행운편지
│   ├── notification/       # 알림
│   └── shop/                # 상점·지갑(골드)
│       └── {controller, dto, entity, mapper, service}
└── global/
    ├── security/         # Spring Security·JWT·OAuth2 설정
    ├── storage/           # Cloudflare R2 presign
    ├── response/           # 공통 응답 봉투 ({success,data}/{success,error})
    ├── exception/          # 공통 예외 처리
    ├── dto/                 # 전역 공용 DTO
    ├── mail/                 # 비밀번호 재설정 메일 발송
    └── time/                  # 서버 시간 유틸
```

Mapper XML은 `src/main/resources/mapper/<도메인>/` 아래 도메인별로 위치합니다.

## 🚀 로컬 실행 방법

```bash
# 1. 시크릿 설정 파일 생성 후 DB·OAuth 정보 입력
cp src/main/resources/application-secret.example.yaml src/main/resources/application-secret.yaml

# 2. 테스트 (Testcontainers가 MySQL 컨테이너를 자동으로 띄웁니다 — Docker 필요)
./gradlew test

# 3. 실행 (http://localhost:8080)
./gradlew bootRun
```

실행 후 Swagger UI(springdoc)에서 API를 확인할 수 있습니다: `http://localhost:8080/swagger-ui/index.html`

## 📡 API 문서

전체 API 계약(요청/응답 스키마, 에러 코드)은 이 저장소가 아니라 `web-design-repository`가 단일 기준(SSOT)입니다.

- [API-CONTRACT.md](https://github.com/Pickeslog/web-design-repository/blob/main/docs/API-CONTRACT.md)
- [DB 설계 (api-spec/)](https://github.com/Pickeslog/web-design-repository/tree/main/api-spec)

## 📝 커밋 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/)를 따릅니다.

```
1. 커밋 유형 지정 (영어 소문자)
   - feat     : 새로운 기능 추가
   - fix      : 버그 수정
   - docs     : 문서 수정
   - style    : 코드 포맷팅, 세미콜론 등 코드 변경이 없는 경우
   - refactor : 코드 리팩토링
   - test     : 테스트 코드 추가/수정
   - chore    : 빌드/설정 등 기타 변경

2. 이슈 번호와 함께 작성
   feat: implement login API (#6)

3. 제목은 영문 기준 50자 이내, 명령형으로 작성
```

## 💻 코드 컨벤션

- 도메인 단위 패키지 구조: `domain/<도메인>/{controller, service, mapper, dto, entity}`
- JPA, `@Entity`, Spring Data Repository는 사용하지 않는다 (MyBatis만 사용)
- 컨트롤러는 얇게 유지하고, 트랜잭션·비즈니스 로직은 서비스 계층에 둔다
- Mapper 인터페이스와 Mapper XML의 `namespace`/statement `id`는 정확히 일치시킨다
- SQL 파라미터는 `#{}`를 사용한다 (`${}`는 원칙적으로 금지)
- 들여쓰기 4칸, Lombok으로 getter/setter·생성자 보일러플레이트를 줄인다

## 🔀 브랜치 전략

[GitHub Flow](https://docs.github.com/ko/get-started/using-github/github-flow)를 따릅니다.

```
feat/<issue번호>-<주제>    예) feat/12-room-invite
fix/<issue번호>-<주제>     예) fix/45-login-token-refresh
chore/<주제>              예) chore/gitignore
```

- 1이슈 = 1브랜치 = 1PR 원칙, `main` 직접 작업 금지
- PR은 코드 리뷰와 CI(빌드·통합테스트) 통과 후 머지

## 🔗 관련 저장소

| 저장소 | 내용 |
|---|---|
| [clov-web](https://github.com/Pickeslog/clov-web) | 프론트엔드 — React SPA |
| [web-design-repository](https://github.com/Pickeslog/web-design-repository) | 화면 명세 · API 계약(SSOT) · DB 설계 |
