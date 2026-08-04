-- 데모 참관자(학원 동기 등)에게 골드를 지급한다. 2026-08-04 리더 요청.
--
-- grant-test-gold.sql 과 목적이 겹치지만 분리한 이유가 있다 — 아래 ★★ 를 볼 것.
--
-- ⚠️⚠️ 이 DB 는 배포된 팀 공용 서비스가 그대로 쓴다. 분리된 dev DB 가 없다(08-02 확인).
--      여기서 바꾸는 값은 즉시 모두에게 보인다. §0 대상 목록을 반드시 눈으로 확인할 것.
--
-- ★★ reason 이 'SIGNUP_GRANT' 가 아니라 'ADMIN_GRANT' 다. 이게 이 파일의 존재 이유다.
--
--   grant-test-gold.sql 은 'SIGNUP_GRANT' 로 기록한다. 그런데 이번 지급액이 하필
--   20,000 이고, reset-signup-grant.sql 이 정확히 이 조합을 대상으로 삼는다.
--
--       reset-signup-grant.sql:  WHERE reason = 'SIGNUP_GRANT' AND amount = 20000
--
--   즉 SIGNUP_GRANT/20000 으로 주면 이 지급이 "옛 시작골드 20,000" 과 구분되지 않는다.
--   누군가 그 정정 스크립트를 한 번만 다시 돌리면 §1-B 가 balance 를 뺄셈이 아니라
--   대입으로 1,000 에 맞추므로, 데모 도중에 참관자 잔액이 통째로 1,000 이 된다.
--   원장 행도 amount=1000 으로 덮어써져서 얼마를 줬는지도 사라진다.
--
--   ★ 금액이 우연히 겹치는 게 문제가 아니라, 사유가 "무엇 때문에 준 돈인지"를
--     구분하지 못하는 게 문제다. 관리자 지급은 처음부터 자기 사유를 가져야 한다.
--
-- ★ 'EARN_' 으로 시작하지 않는 것도 의도다. 계약 §15-4 의 하루 획득 상한은
--   reason LIKE 'EARN\_%' 로 합산한다. EARN_ 계열로 주면 그날 정상 획득이 통째로 막힌다.
--   ADMIN_GRANT 는 그 합산에서 빠진다.
--   ⚠️ clov-api#92(골드 지급 구현)를 머지할 때 합산 조건이 사유 나열식이면 이 규칙이
--     깨진다. 반드시 접두사 방식(LIKE 'EARN\_%')인지 확인할 것.
--
-- ★ 가격을 내리지 않고 지갑을 채우는 이유 — 가격을 내리면 등급 체계가 무너지고,
--   되돌리기 전에 산 계정에는 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   지갑은 §3 으로 언제든 되돌릴 수 있다.
--
-- ★ 08-02 사고 방지 — START TRANSACTION ~ COMMIT 은 반드시 한 번에 실행할 것.
--   중간에서 멈춰 눈검사를 하면 Workbench 가 유휴 연결을 끊으면서 트랜잭션이 조용히
--   롤백되고, 그 뒤의 COMMIT 은 새 연결에서 도는 빈 명령이라 성공한 것처럼 보인다.
--   확인은 §0(실행 전)과 §2(실행 후)에서만 한다.

SET NAMES utf8mb4;

SET @grant_amount = 20000;   -- 더하는 값이다. 잔액을 이 값으로 맞추는 게 아니다.

-- ─────────────────────────────────────────────────────────────
-- §0. 대상 확인 — 이걸 안 보고 §1 을 돌리지 말 것
-- ─────────────────────────────────────────────────────────────
-- 기본 대상: 오늘 가입한 사람 전부(= 데모하러 들어온 동기들).
-- 팀 계정은 보통 이전에 만들었으므로 안 걸린다. 팀원도 주려면 §0-B 를 쓴다.
SELECT u.id, u.email, u.nickname, u.created_at,
       COALESCE(w.balance, 0) AS balance_before,
       IF(w.user_id IS NULL, '지갑 없음(만들어짐)', '') AS 비고
FROM users u
LEFT JOIN user_wallets w ON w.user_id = u.id
WHERE u.created_at >= CURDATE()
ORDER BY u.created_at;

-- ★ 위 목록이 지급 대상 전부다. 모르는 계정이 섞여 있으면 멈추고 §0-B 로 바꾼다.
-- ★ 0행이면 오늘 가입자가 없다는 뜻이다. 그대로 돌리면 아무 일도 안 일어난다.

-- §0-B. 이메일로 직접 지정하고 싶을 때 — 위 WHERE 절을 아래로 바꾼다.
--   SET @target_emails = 'a@x.com,b@y.com';
--   ... WHERE FIND_IN_SET(u.email, @target_emails)
--   §1 의 세 문장에도 똑같이 바꿔 넣어야 한다. 하나만 바꾸면 대상이 어긋난다.

-- ─────────────────────────────────────────────────────────────
-- §1. 지급 — 여기부터 COMMIT 까지 한 번에 실행
-- ─────────────────────────────────────────────────────────────
START TRANSACTION;

-- 지갑이 없으면 만든다(0 으로 시작 — 지급은 아래 UPDATE 가 한다).
-- 지갑을 미리 만들어도 시작골드 1,000 을 뺏는 게 아니다: getOrCreateWallet 은 행이
-- 있으면 지급을 건너뛰는데, 어차피 20,000 이 1,000 보다 크다.
INSERT INTO user_wallets (user_id, balance)
SELECT u.id, 0
FROM users u
WHERE u.created_at >= CURDATE()
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

UPDATE user_wallets w
JOIN users u ON u.id = w.user_id
SET w.balance = w.balance + @grant_amount
WHERE u.created_at >= CURDATE();

-- 원장에도 남긴다. 잔액만 바꾸고 원장을 빼면 나중에 대사가 안 맞는다.
INSERT INTO wallet_transactions (user_id, reason, amount, balance_after, reference_id)
SELECT w.user_id, 'ADMIN_GRANT', @grant_amount, w.balance, NULL
FROM user_wallets w
JOIN users u ON u.id = w.user_id
WHERE u.created_at >= CURDATE();

COMMIT;
-- Output 에서 세 문장의 rows affected 만 보고 바로 COMMIT 한다. 눈검사는 §2 에서.

-- ─────────────────────────────────────────────────────────────
-- §2. 검증 — 반드시 눈으로 확인
-- ─────────────────────────────────────────────────────────────
-- balance 가 §0 대비 정확히 20,000 늘었는지 본다.
-- 안 늘었으면 트랜잭션이 롤백된 것이다(위 ★ 참고) — §1 을 한 번에 다시 실행한다.
SELECT u.id, u.email, w.balance,
       (SELECT COUNT(*) FROM wallet_transactions t
         WHERE t.user_id = u.id AND t.reason = 'ADMIN_GRANT') AS admin_grants
FROM users u
JOIN user_wallets w ON w.user_id = u.id
WHERE u.created_at >= CURDATE()
ORDER BY u.created_at;

-- ★ admin_grants 가 2 이상인 사람이 있으면 §1 을 두 번 돌린 것이다.
--   그만큼 잔액도 두 번 들어갔다. §3 으로 한 번치를 빼면 된다.

-- 지갑 분포 — 잔액이 카탈로그 총액(52,000)을 넘는 사람이 생기면 상점이 의미를 잃는다.
SELECT COUNT(*) AS wallets, MIN(balance) AS min_bal, MAX(balance) AS max_bal,
       (SELECT SUM(ROUND(price * (100 - discount_rate) / 100))
          FROM shop_items WHERE status = 'ACTIVE') AS active_catalog_total
FROM user_wallets;

-- ─────────────────────────────────────────────────────────────
-- §3. 되돌리기 — 데모가 끝나면
-- ─────────────────────────────────────────────────────────────
-- 구매까지 했다면 잔액을 되돌려도 보유함에는 아이템이 남는다(정상이다 — 산 건 산 것이다).
-- reason 이 ADMIN_GRANT 라 시작골드·획득분은 안 건드린다.
--
-- START TRANSACTION;
-- UPDATE user_wallets w
--   JOIN (SELECT user_id, SUM(amount) AS granted FROM wallet_transactions
--          WHERE reason = 'ADMIN_GRANT' GROUP BY user_id) g ON g.user_id = w.user_id
--   SET w.balance = GREATEST(w.balance - g.granted, 0);
-- DELETE FROM wallet_transactions WHERE reason = 'ADMIN_GRANT';
-- COMMIT;
