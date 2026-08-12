-- 테스트 계정에 골드를 채운다. 상점 구매·장착을 실제로 눌러보기 위한 용도다.
--
-- 왜 가격을 내리지 않고 지갑을 채우나
--   가격을 내리면 카탈로그 등급 체계가 무너지고, 되돌리기 전에 산 계정에는
--   user_inventory_items.paid_price 에 낮은 값이 그대로 남는다(구매 시점 가격이 고정된다).
--   반대로 지갑은 이 스크립트로 언제든 되돌릴 수 있다.
--
-- ★ 08-02 사고 방지 — START TRANSACTION ~ COMMIT 은 반드시 한 번에 실행할 것.
--   중간에서 멈춰 있으면 클라이언트가 유휴 연결을 끊으면서 트랜잭션이 조용히 롤백되고,
--   그 뒤의 COMMIT 은 새 연결에서 도는 빈 명령이라 '0 rows affected' 로 성공한 것처럼 보인다.
--   확인은 §0(실행 전)과 §2(실행 후)에서 하고, 트랜잭션 안에서는 눈검사를 하지 않는다.
--
-- ★ 이 DB 는 배포된 팀 공용 서비스가 그대로 쓴다. 분리된 dev DB 가 없다(08-02 확인).
--   아래 대상 지정을 반드시 확인하고 돌릴 것.

SET NAMES utf8mb4;

-- ─────────────────────────────────────────────────────────────
-- §0. 실행 전 확인 — 대상과 현재 잔액
-- ─────────────────────────────────────────────────────────────
-- 여기 이메일을 본인 계정으로 바꾼다. 여러 명이면 IN ('a@x','b@y') 로 늘린다.
SET @target_emails = 'CHANGE_ME@example.com';
SET @grant_amount  = 50000;   -- 지급할 금액(더하기다. 잔액을 특정 값으로 맞추는 게 아니다)

SELECT u.id, u.email, COALESCE(w.balance, 0) AS balance_before
FROM users u
LEFT JOIN user_wallets w ON w.user_id = u.id
WHERE FIND_IN_SET(u.email, @target_emails);

-- 위 결과가 비어 있으면 이메일이 틀린 것이다. 아래를 돌리지 말 것.
-- balance 가 NULL 이면 지갑이 아직 없다 — 상점을 한 번 열면 자동 생성된다
-- (ShopService.getOrCreateWallet). §1 이 지갑이 없는 경우도 만들어 준다.

-- ─────────────────────────────────────────────────────────────
-- §1. 지급 — 여기부터 COMMIT 까지 한 번에 실행
-- ─────────────────────────────────────────────────────────────
START TRANSACTION;

-- 지갑이 없으면 만든다(잔액 0으로 시작 — 지급은 아래 UPDATE 가 한다)
INSERT INTO user_wallets (user_id, balance)
SELECT u.id, 0
FROM users u
WHERE FIND_IN_SET(u.email, @target_emails)
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

UPDATE user_wallets w
JOIN users u ON u.id = w.user_id
SET w.balance = w.balance + @grant_amount
WHERE FIND_IN_SET(u.email, @target_emails);

-- 원장에도 남긴다. 잔액만 바꾸고 원장을 빼면 나중에 대사가 안 맞는다.
--
-- reason 에 SIGNUP_GRANT 를 쓰는 이유 — 계약 §15-4 의 reason 은 SIGNUP_GRANT / PURCHASE /
-- EARN_MASCOT / EARN_MEMORY 넷뿐이고 관리자 지급용 사유가 없다. EARN_ 계열을 쓰면 하루 획득
-- 상한(500) 합산에 잡혀 그날 정상 획득이 막힌다. SIGNUP_GRANT 는 그 합산에서 빠진다.
-- reset-signup-grant.sql 은 amount=20000 인 행만 찾으므로 이 행과 겹치지 않는다.
INSERT INTO wallet_transactions (user_id, reason, amount, balance_after, reference_id)
SELECT w.user_id, 'SIGNUP_GRANT', @grant_amount, w.balance, NULL
FROM user_wallets w
JOIN users u ON u.id = w.user_id
WHERE FIND_IN_SET(u.email, @target_emails);

COMMIT;

-- ─────────────────────────────────────────────────────────────
-- §2. 실행 후 검증 — 반드시 눈으로 확인한다
-- ─────────────────────────────────────────────────────────────
SELECT u.id, u.email, w.balance,
       (SELECT COUNT(*) FROM wallet_transactions t WHERE t.user_id = u.id) AS ledger_rows
FROM users u
JOIN user_wallets w ON w.user_id = u.id
WHERE FIND_IN_SET(u.email, @target_emails);

-- balance 가 §0 대비 @grant_amount 만큼 늘었으면 성공이다.
-- 안 늘었으면 트랜잭션이 롤백된 것이다(위 ★ 참고) — §1 을 한 번에 다시 실행한다.

-- ─────────────────────────────────────────────────────────────
-- §3. 되돌리기 — 테스트가 끝나면
-- ─────────────────────────────────────────────────────────────
-- 구매까지 했다면 잔액만 되돌려도 보유함에는 아이템이 남는다. 아이템까지 지우려면
-- user_preferences.equipped_item_id 를 먼저 NULL 로 풀어야 FK 에 안 걸린다.
--
-- START TRANSACTION;
-- UPDATE user_wallets w JOIN users u ON u.id = w.user_id
--   SET w.balance = GREATEST(w.balance - @grant_amount, 0)
--   WHERE FIND_IN_SET(u.email, @target_emails);
-- DELETE t FROM wallet_transactions t JOIN users u ON u.id = t.user_id
--   WHERE FIND_IN_SET(u.email, @target_emails)
--     AND t.reason = 'SIGNUP_GRANT' AND t.amount = @grant_amount;
-- COMMIT;
