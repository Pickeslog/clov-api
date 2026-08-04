-- [2/2] users.personal_invite_code 제거 — 2단계: 컬럼을 지운다.
--
-- ★★ 이 파일을 실행하기 전에 반드시 확인할 것 — 되돌릴 수 없는 유일한 단계다.
--
--   ① step1(NULL 허용) 을 실행했다
--   ② 컬럼을 안 읽고 안 쓰는 코드가 **배포까지** 됐다 (머지가 아니라 배포다)
--
--   ②를 안 지키면 로그인이 죽는다. UserMapper.xml 이 SELECT 절에 이 컬럼을
--   이름으로 명시하기 때문에, 컬럼이 없어지는 순간 사용자 조회가 전부 실패한다.
--   "머지했으니 됐겠지"로 이 파일을 돌리지 말 것.
--
-- ⚠️ 지워지는 값에 대해: 모든 사용자에게 'CLV-XXXXXX' 가 하나씩 들어 있지만
--   전부 아무도 쓴 적 없는 난수다. 이 코드를 입력받는 화면도 API 도 없었으므로
--   누구의 무엇도 이 값에 묶여 있지 않다. 나중에 '개인 코드로 초대' 기능을
--   만들게 되면 그때 새로 생성하면 되고, 그래도 바뀌는 사람이 없다.
--
-- UNIQUE KEY uk_users_invite_code 는 컬럼과 함께 자동으로 사라진다 —
-- 이 컬럼 하나로만 이뤄진 인덱스라 따로 DROP INDEX 를 쓰지 않는다.

SET NAMES utf8mb4;

-- ── 사전점검 1: step1 이 끝났는지 ────────────────────────────────────────
-- IS_NULLABLE 이 'YES' 여야 한다. 'NO' 면 step1 을 먼저 실행한다.
-- 0행이면 이미 지워진 것이다.
SELECT COLUMN_NAME, IS_NULLABLE,
       IF(IS_NULLABLE = 'YES', 'step1 완료', '★ step1 먼저') AS chk
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'personal_invite_code';

-- ── 사전점검 2: 배포된 코드가 이미 이 컬럼을 안 쓰는지 ───────────────────
-- ★ 이 쿼리가 배포 여부를 대신 답해준다.
--   step1 이후에 가입한 사람이 있는데 그 행의 값이 NULL 이면, 배포된 코드가
--   이 컬럼을 더 이상 안 넣고 있다는 뜻이다 — 지워도 안전하다.
--   반대로 최근 행에 값이 들어 있으면 옛 코드가 아직 돌고 있는 것이다.
-- 판단이 애매하면(최근 가입자가 없으면) 지우지 말고 배포 로그를 직접 확인할 것.
SELECT COUNT(*)                             AS recent_signups,
       SUM(personal_invite_code IS NULL)    AS null_rows,
       SUM(personal_invite_code IS NOT NULL) AS still_filled
FROM users
WHERE created_at >= NOW() - INTERVAL 1 DAY;

-- ── 삭제 ────────────────────────────────────────────────────────────────
ALTER TABLE users DROP COLUMN personal_invite_code;

-- ── 검증 1: 컬럼이 사라져야 한다 (0행) ──────────────────────────────────
SELECT COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'personal_invite_code';

-- ── 검증 2: 인덱스도 같이 사라져야 한다 (0행) ───────────────────────────
SELECT INDEX_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND INDEX_NAME = 'uk_users_invite_code';

-- ── 검증 3: 남은 UNIQUE 두 개는 그대로여야 한다 ─────────────────────────
-- uk_users_email · uk_users_oauth 가 나와야 한다. 하나라도 없으면 즉시 멈출 것.
SELECT DISTINCT INDEX_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND NON_UNIQUE = 0
ORDER BY INDEX_NAME;

-- ── 검증 4: 사용자 조회가 살아 있는지 ───────────────────────────────────
-- 여기서 에러가 나면 배포된 코드가 아직 이 컬럼을 참조하는 것이다.
-- 그 경우 컬럼을 되살려야 한다:
--   ALTER TABLE users ADD COLUMN personal_invite_code VARCHAR(20) NULL;
--   UPDATE users SET personal_invite_code = CONCAT('CLV-', UPPER(SUBSTRING(MD5(id), 1, 6)));
--   ALTER TABLE users ADD UNIQUE KEY uk_users_invite_code (personal_invite_code);
-- (값은 새로 만드는 것이고 원래 값은 못 돌린다 — 그래도 아무도 안 쓰던 값이라 무해하다)
SELECT COUNT(*) AS total_users FROM users;
