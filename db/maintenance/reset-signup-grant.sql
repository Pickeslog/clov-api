-- ============================================================
-- Clov — 시작 골드 20,000 지갑 정정 (clov-api#105)
--
-- ⚠️⚠️ 이 DB는 배포된 팀 공용 서비스가 그대로 쓰는 DB다. 분리된 dev DB가 없다.
--       여기서 바꾸는 값은 팀원들에게 즉시 보인다. "dev니까 괜찮다"가 성립하지 않는다.
--       (2026-08-02 확인 — 대상 13계정에 리더·chacha 실계정이 포함돼 있었다)
-- ⚠️ 실행 전에 반드시 §0 진단을 눈으로 확인할 것. §1·§1-B·§2는 서로 배타적이다.
-- ⚠️ 남의 잔액을 건드리는 작업이다. 실행 전에 팀에 알릴 것.
-- ⚠️ §0~§3을 같은 커넥션(같은 세션)에서 실행할 것 — TEMPORARY TABLE을 쓴다.
--
-- 배경
--   ShopService.SIGNUP_GRANT_AMOUNT 는 #100에서 20,000 → 1,000 으로 정정됐지만,
--   이 값은 지갑을 "만들 때" 한 번만 쓰인다(getOrCreateWallet). 그 전에 상점을 한 번이라도
--   연 계정은 20,000 을 그대로 들고 있다. 카탈로그 총액이 42,260 이라 그 절반이고,
--   계약 §15-4 의 획득 규칙(하루 500)이 무의미해진다.
--
--   지갑은 "상점에 들어가면" 자동 생성되므로, 대상은 생각보다 많을 수 있다.
-- ============================================================

SET @OLD_GRANT = 20000;
SET @NEW_GRANT = 1000;


-- ============================================================
-- §0. 진단 — 먼저 이것부터 본다
-- ============================================================

-- 0-1. 전체 지갑 분포
SELECT COUNT(*) AS wallets, MIN(balance) AS min_bal, MAX(balance) AS max_bal, SUM(balance) AS total_bal
FROM user_wallets;

-- 0-2. 옛 시작 골드를 받은 계정 (원장 기준 — balance 가 아니라 지급 기록으로 판정한다.
--      구매·획득으로 balance 는 이미 움직였을 수 있어서 balance = 20000 으로 찾으면 놓친다)
DROP TEMPORARY TABLE IF EXISTS tmp_old_grant_users;
CREATE TEMPORARY TABLE tmp_old_grant_users (user_id BIGINT PRIMARY KEY);
INSERT INTO tmp_old_grant_users (user_id)
SELECT DISTINCT user_id
FROM wallet_transactions
WHERE reason = 'SIGNUP_GRANT' AND amount = @OLD_GRANT;

-- 0-3. 대상 미리보기 — 정정하면 얼마가 되는지, 0으로 깎이는 사람은 누구인지
SELECT u.id, u.email, u.nickname,
       w.balance                                              AS 현재잔액,
       GREATEST(0, w.balance - (@OLD_GRANT - @NEW_GRANT))     AS 정정후잔액,
       CASE WHEN w.balance - (@OLD_GRANT - @NEW_GRANT) < 0
            THEN '⚠️ 이미 1,000 넘게 썼음 — 0으로 내려감' ELSE '' END AS 비고,
       (SELECT COUNT(*) FROM user_inventory_items i WHERE i.user_id = u.id) AS 보유아이템
FROM tmp_old_grant_users t
JOIN users u        ON u.id = t.user_id
JOIN user_wallets w ON w.user_id = t.user_id
ORDER BY w.balance DESC;

-- 0-4. 되돌리기용 스냅샷 — 실DB라서 어떤 옵션을 고르든 이걸 먼저 만든다.
--      임시 테이블이 아니라 실제 테이블이다(세션이 끊겨도 남아야 한다).
CREATE TABLE IF NOT EXISTS _bak_user_wallets_20260802 AS
SELECT user_id, balance FROM user_wallets;

SELECT COUNT(*) AS 백업된_지갑 FROM _bak_user_wallets_20260802;
-- 되돌리려면:
--   UPDATE user_wallets w JOIN _bak_user_wallets_20260802 b ON b.user_id = w.user_id
--      SET w.balance = b.balance;
-- 정리는 데모가 끝난 뒤에:  DROP TABLE _bak_user_wallets_20260802;


-- ============================================================
-- §1-B. [옵션 C · 권장] 잔액만 1,000으로 맞추고 보유 아이템은 남긴다
--
--   §1(A)은 "20,000 - 19,000" 뺄셈이라, 이미 1,000 넘게 쓴 계정이 0 으로 떨어진다.
--   2026-08-02 실측에서 리더(18,320)와 chacha(7,800)가 정확히 거기 걸렸다.
--   시연 계정이 0 골드면 상점이 고장난 것처럼 보여서 데모에는 A 를 쓸 수 없다.
--
--   B 는 반대로 지갑·원장·보유함을 지우는데, 실DB라서 팀원의 구매 이력이 사라진다.
--
--   그래서 이 옵션: 잔액을 뺄셈이 아니라 대입으로 1,000 에 맞추고 보유 아이템은 둔다.
--   이미 쓴 사람은 아이템도 갖고 1,000 도 받지만, 목적이 "계약대로 된 시작 상태"이고
--   삭제가 없어 되돌릴 수 있으므로 이쪽이 맞다.
-- ============================================================
START TRANSACTION;

UPDATE user_wallets w
JOIN tmp_old_grant_users t ON t.user_id = w.user_id
SET w.balance = @NEW_GRANT;

-- 원장의 지급 기록도 고친다. 안 고치면 다음에 이 스크립트를 다시 돌릴 때
-- 같은 계정이 또 대상으로 잡힌다(§1 과 같은 이유).
UPDATE wallet_transactions
SET amount = @NEW_GRANT, balance_after = @NEW_GRANT
WHERE reason = 'SIGNUP_GRANT' AND amount = @OLD_GRANT;

-- 커밋 전에 확인 — 전부 1,000 이어야 한다
SELECT u.id, u.email, w.balance
FROM tmp_old_grant_users t
JOIN users u        ON u.id = t.user_id
JOIN user_wallets w ON w.user_id = t.user_id
ORDER BY u.id;

COMMIT;
-- 문제가 보이면 COMMIT 대신: ROLLBACK;


-- ============================================================
-- §1. [옵션 A · 보수적] 시작 골드만 정정한다
--
--   구매 이력과 보유 아이템은 그대로 두고 잔액만 19,000 내린다.
--   실제 사용자가 쓰던 계정이 섞여 있을 때 이쪽을 쓴다.
--
--   ⚠️ 이미 1,000 넘게 쓴 계정은 0 이 된다(음수 방지). §0-3 의 '비고' 열을 먼저 볼 것.
--      그 계정은 "20,000 이 있었기에 가능했던 구매"를 이미 한 상태라 완전한 원복은 불가능하다.
--
--   ⚠️ 2026-08-02 에는 이 옵션을 쓰지 않았다. 대상 13계정 중 4계정이 0 으로 떨어졌고
--      거기에 시연에 쓸 리더·chacha 계정이 포함돼 있었다. §1-B 를 썼다.
--      "잔액이 20,000 그대로인 계정만" 남았을 때 쓰기 좋은 옵션이다.
-- ============================================================
START TRANSACTION;

UPDATE user_wallets w
JOIN tmp_old_grant_users t ON t.user_id = w.user_id
SET w.balance = GREATEST(0, w.balance - (@OLD_GRANT - @NEW_GRANT));

-- 원장도 같이 고친다. 안 고치면 "지급 20,000"이 기록에 남아 나중에 이 스크립트를
-- 다시 돌릴 때 같은 계정이 또 대상으로 잡혀 두 번 깎인다.
--
-- ⚠️ 한계 — 이 지급 이후에 쌓인 거래들의 balance_after 는 다시 계산하지 않는다.
--    그 값들은 20,000 기준으로 찍혀 있어서 원장을 위에서 아래로 더해가면 최종 잔액과
--    안 맞는다. 지금 원장을 읽어서 잔액을 재계산하는 코드는 없고(ShopService 는 항상
--    user_wallets.balance 를 본다) 화면에 노출되는 이력도 없어서 실사용에는 영향이 없다.
--    나중에 거래내역 화면을 만들면 그때 재계산이 필요하다.
UPDATE wallet_transactions
SET amount = @NEW_GRANT, balance_after = @NEW_GRANT
WHERE reason = 'SIGNUP_GRANT' AND amount = @OLD_GRANT;

COMMIT;
-- 문제가 보이면 COMMIT 대신: ROLLBACK;


-- ============================================================
-- §2. [옵션 B · 데모용 전체 리셋] — §1 을 실행했다면 이건 하지 않는다
--
--   지갑·원장·보유함을 지운다. 다음에 상점에 들어가면 1,000 으로 새로 생성된다.
--   발표용으로 깨끗한 시작 상태를 만들 때 쓴다.
--
--   ⚠️ 구매 이력과 보유 아이템이 사라진다. 되돌릴 수 없다.
-- ============================================================
-- START TRANSACTION;
--
-- -- 보유하지 않게 될 아이템을 장착 중인 사람이 남지 않도록 먼저 해제한다.
-- -- (equipped_item_id 의 FK 는 shop_items 라 제약 위반은 안 나지만, 안 가진 코스튬을
-- --  입고 있는 상태가 되어 화면과 보유함이 어긋난다.)
-- UPDATE user_preferences SET equipped_item_id = NULL
--  WHERE user_id IN (SELECT user_id FROM tmp_old_grant_users);
--
-- DELETE FROM wallet_transactions  WHERE user_id IN (SELECT user_id FROM tmp_old_grant_users);
-- DELETE FROM user_inventory_items WHERE user_id IN (SELECT user_id FROM tmp_old_grant_users);
-- DELETE FROM user_wallets         WHERE user_id IN (SELECT user_id FROM tmp_old_grant_users);
--
-- COMMIT;


-- ============================================================
-- §3. 검증 — 남은 20,000 지급 기록이 0 이어야 한다
-- ============================================================
SELECT 'SIGNUP_GRANT=20000 남은 건수' AS check_item,
       COUNT(*) AS cnt
FROM wallet_transactions
WHERE reason = 'SIGNUP_GRANT' AND amount = @OLD_GRANT
UNION ALL
SELECT '카탈로그 총액보다 잔액이 큰 지갑', COUNT(*)
FROM user_wallets
WHERE balance > (SELECT COALESCE(SUM(price), 0) FROM shop_items WHERE status = 'ACTIVE');

-- 정정 후 분포
SELECT COUNT(*) AS wallets, MIN(balance) AS min_bal, MAX(balance) AS max_bal, SUM(balance) AS total_bal
FROM user_wallets;

DROP TEMPORARY TABLE IF EXISTS tmp_old_grant_users;
