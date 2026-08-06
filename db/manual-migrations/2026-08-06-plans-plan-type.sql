-- plans.plan_type 신설 — 생일 약속을 평범한 약속과 구분한다 (계약 §8-1, clov-api#144).
--
-- 배경: 일정계획에 생일이 다가오면 칩이 뜨고(clov-web#380, 배포됨), 그 칩을 눌러
--   사용자가 생일 약속을 만들 수 있게 할 예정이다(clov-web#381). 저장된 뒤에 그 약속을
--   황금 티켓으로 그리려면 "이건 생일 약속"이라는 표시가 응답에 있어야 한다.
--
--   제목으로 판별  → 사용자가 제목을 고치면 깨진다
--   날짜로 판별    → 그날 잡힌 다른 약속까지 금색이 된다
--   ────────────────────────────────────────────────
--   그래서 컬럼 하나를 둔다.
--
-- ★ 시스템이 만드는 약속이 아니다. 사람이 칩을 눌러 만든다 — writer_id 는 누른 사람이고
--   기존 권한 규칙(수정·삭제는 작성자 본인)이 그대로 돈다. clov-api#143 에서 자동 생성을
--   접었던 이유(작성자 없는 행 · 멱등 · 배치 · 서버 UTC 어긋남)가 하나도 돌아오지 않는다.
--
-- ★ 이 값은 화면 표시용 힌트지 보안 경계가 아니다. 서버는 plan_date 가 실제로 누군가의
--   생일인지 검증하지 않는다(생일은 바뀌고 멤버는 나간다 — 검증하면 약속 생성이 멤버
--   데이터에 묶인다). §15 배경 상품의 잠금을 "화면 안내"로 둔 것과 같은 판단이다.
--
-- ⚠️ DEFAULT 'NORMAL' 이라 기존 행 백필이 필요 없다. 별도 UPDATE 를 돌리지 말 것.
--   NOT NULL 이므로 응답에서도 항상 값이 있다(편지 title 처럼 생략되지 않는다).
--
-- ⚠️ 배포 순서: 이 SQL 을 먼저 돌리고 코드를 배포한다.
--   컬럼이 없는 상태로 새 코드가 뜨면 INSERT 와 SELECT 가 둘 다 죽는다(매퍼가 컬럼을
--   이름으로 명시한다). 반대로 컬럼만 먼저 만들어두는 것은 옛 코드에 아무 영향이 없다 —
--   DEFAULT 가 있어서 컬럼을 모르는 INSERT 도 그대로 성공한다.

SET NAMES utf8mb4;

-- ── 사전점검: 아직 없어야 한다 ──────────────────────────────────────────
-- 행이 나오면 이미 실행된 것이다(이 SQL 은 다시 돌려도 에러만 날 뿐 해롭지 않다).
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'plans'
  AND COLUMN_NAME = 'plan_type';

-- ── 컬럼 추가 ───────────────────────────────────────────────────────────
-- VARCHAR(20) — status·memory_status 와 같은 폭. ENUM 을 쓰지 않는 것도 같은 이유다
-- (값이 늘 때 ALTER 없이 넣을 수 있어야 한다. 계약이 "값은 앞으로 늘 수 있다"로 열어뒀다).
ALTER TABLE plans
  ADD COLUMN plan_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
  COMMENT 'NORMAL/BIRTHDAY — 약속의 종류(계약 §8-1). 표시용 힌트지 권한과 무관하다'
  AFTER memory_status;

-- ── 검증 1: 컬럼이 규격대로 생겼는가 ────────────────────────────────────
-- IS_NULLABLE='NO' · COLUMN_DEFAULT='NORMAL' 이어야 한다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
       IF(IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = 'NORMAL', 'OK', '★ 확인') AS chk
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'plans'
  AND COLUMN_NAME = 'plan_type';

-- ── 검증 2: 기존 약속이 전부 NORMAL 인가 ────────────────────────────────
-- not_normal 이 0 이어야 한다. 백필을 안 했는데도 0 인 것이 DEFAULT 가 먹었다는 증거다.
SELECT COUNT(*) AS total_plans,
       SUM(plan_type <> 'NORMAL') AS not_normal,
       IF(SUM(plan_type <> 'NORMAL') = 0, 'OK', '★ 확인') AS chk
FROM plans;
