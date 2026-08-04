-- [1/2] users.personal_invite_code 제거 — 1단계: NULL 을 허용한다.
--
-- 배경: 이 컬럼은 2026-07-20 인증 API 첫 구현(70d1fd6)부터 있었는데, 2주 넘게
-- 아무것도 이 값으로 사람을 찾지 않았다. 만드는 쪽만 완성돼 있었다.
--
--   생성      AuthService — 가입할 때 'CLV-' + 6자리
--   중복확인  existsByPersonalInviteCode — 생성할 때만 쓴다. 이게 유일한 쿼리다
--   노출      UserResponse · UserProfileResponse → 사용자설정에 readOnly
--   ────────────────────────────────────────────────────────────────
--   소비      없음. 받는 API 도 화면도 계약 규정도 없다
--
-- ★ 방 초대코드와 다른 물건이다. 헷갈리지 말 것.
--     개인  users.personal_invite_code   'CLV-XXXXXX'        ← 이 파일이 지우는 것
--     방    invites.invite_code          'CLV-JOIN-XXXXXX'   ← 멀쩡히 쓰인다
--   화면에 '내 초대코드'라고 적혀 있으니 사용자는 그걸 방 입장 칸에 넣어본다.
--   테이블도 접두사도 달라서 그냥 실패한다. 그게 이걸 지우는 이유다.
--
-- ★★ 순서를 반드시 지킬 것. 두 방향 다 서비스가 죽는다.
--
--   생성만 먼저 지우면 → 가입이 깨진다.
--     컬럼이 NOT NULL 이고 기본값이 없어서, AuthService 가 코드를 안 만들면
--     INSERT 가 통째로 실패한다.
--
--   컬럼을 먼저 DROP 하면 → 로그인까지 깨진다.
--     UserMapper.xml 이 세 군데 SELECT 에서 이 컬럼을 이름으로 명시한다.
--     컬럼이 사라지면 사용자 조회가 전부 죽는다 — 로그인, /users/me, 전부.
--
--   그래서 이 순서뿐이다.
--     1) 이 파일        NULL 허용            ← DB 먼저. 코드는 아직 그대로 넣고 있다
--     2) 코드 배포      생성·매퍼·DTO·화면   ← 이 시점부터 새 행은 NULL 이 된다
--     3) step2 파일     DROP COLUMN          ← 코드가 안 보게 된 뒤에만
--
-- ⚠️ 1단계와 2단계 사이에는 서비스가 정상이다. 컬럼은 있고 코드도 넣는다.
--   NULL 을 허용만 해두는 것이라 이 SQL 자체는 아무것도 안 망가뜨린다.
--   되돌리려면 MODIFY ... NOT NULL 로 돌리면 된다(그 사이 NULL 행이 없을 때만).

SET NAMES utf8mb4;

-- ── 사전점검: 지금 상태 ──────────────────────────────────────────────────
-- IS_NULLABLE 이 'NO' 여야 한다. 이미 'YES' 면 이 SQL 은 실행된 것이다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'personal_invite_code';

-- ── NULL 허용 ───────────────────────────────────────────────────────────
-- UNIQUE 인덱스(uk_users_invite_code)는 그대로 둔다. MySQL 의 UNIQUE 는 NULL 중복을
-- 허용하므로, 2단계 배포 후 새 행이 전부 NULL 이어도 충돌하지 않는다.
-- 인덱스는 step2 에서 컬럼과 함께 사라진다.
ALTER TABLE users MODIFY COLUMN personal_invite_code VARCHAR(20) NULL;

-- ── 검증 1: IS_NULLABLE 이 'YES' 로 바뀌어야 한다 ────────────────────────
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY,
       IF(IS_NULLABLE = 'YES', 'OK', '★ 확인') AS chk
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'personal_invite_code';

-- ── 검증 2: 기존 행은 그대로여야 한다 ────────────────────────────────────
-- null_rows 가 0 이어야 한다. NULL 허용으로 바꿨을 뿐 값을 지운 게 아니다.
SELECT COUNT(*) AS total_users,
       SUM(personal_invite_code IS NULL) AS null_rows
FROM users;
