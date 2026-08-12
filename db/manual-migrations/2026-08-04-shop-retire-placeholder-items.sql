-- 초기 플레이스홀더 상품 11종을 판매 종료(RETIRED)한다.
--
-- 배경: 2026-07-29-shop-tables.sql 로 넣은 시드 12종은 실제 에셋 없이 이름과 가격만 있는
-- 자리표시용이었다(image_url 도 SVG 한 장짜리). 그 사이 상태 9종을 갖춘 진짜 마스코트 스킨이
-- 12종 들어와서, 자리표시 상품이 카탈로그를 절반 차지한 채 남아 있을 이유가 없어졌다.
--
-- 시드 12종 중 COSTUME_ROB_EXPLORER 는 이미 스킨으로 덮여 대상이 아니다
-- (2026-08-04-shop-four-skins... 의 code 충돌 — 계약 §15-1 경고 참조). 나머지 11종이 대상이다.
--
-- ══════════════════════════════════════════════════════════════════════
-- ★ DELETE 가 아니라 RETIRED 인 이유
-- ══════════════════════════════════════════════════════════════════════
--
-- shop_items 를 FK 로 잡는 곳이 둘이고 둘 다 RESTRICT 다.
--
--   user_inventory_items.item_id        fk_user_inventory_items_item
--   user_preferences.equipped_item_id   fk_user_preferences_equipped_item
--
-- 산 사람이 하나라도 있으면 DELETE 는 그냥 실패한다. 억지로 지우려면 남의 인벤토리와
-- 장착 상태를 먼저 지워야 하는데, 그건 골드를 내고 받은 것을 뺏는 것이다. 환불 로직도 없고
-- wallet_transactions 의 PURCHASE 행은 없는 상품을 가리킨 채 남는다.
--
-- RETIRED 는 계약 §15-1 이 정의해 둔 정식 수단이다.
--   * 목록에서 사라진다(ShopMapper.findCatalog 가 status='ACTIVE' 만 조회 — 이 배포에 포함)
--   * 구매가 막힌다(ShopItem.isPurchasable() 이 ACTIVE 만 허용 → ITEM_NOT_PURCHASABLE)
--   * 이미 산 사람은 그대로 갖는다(findInventory 는 status 를 안 본다 — 의도된 것)
--   * 되돌릴 수 있다(status='ACTIVE' 한 줄)
--
-- ⚠️ 이 SQL 은 findCatalog 수정이 배포된 뒤에 실행해야 한다. 순서가 반대면 RETIRED 상품이
--   목록에 그대로 뜨면서 누르면 실패하는 "보이는데 못 사는" 상태가 된다.
--
-- ══════════════════════════════════════════════════════════════════════
-- ⚠️ 카탈로그가 절반이 된다 — 획득량을 다시 볼 것
-- ══════════════════════════════════════════════════════════════════════
--
--   실행 전  71,860  (시드 11종 37,060 + 스킨 12종 34,800)
--   실행 후  34,800  (스킨 12종만)
--
--   완주 일수 = (34,800 - 1,000) / 3,000 = 약 12일   ← 오늘 정한 §15-4 는 24일 기준이었다
--
-- 계약 §15-4 에 "획득량을 늘리기 전에 카탈로그를 먼저 늘리는지 본다"고 적어뒀는데 방향만
-- 반대일 뿐 같은 이야기다. **하루 상한 3,000 을 그대로 두면 2주 안에 살 게 없어진다.**
-- 이 SQL 은 상한을 건드리지 않는다 — 리더가 §15-4 를 다시 정할 때까지는 값이 그대로다.
--
-- 같이 사라지는 것 둘도 확인할 것.
--   * SKIN 카테고리 4종 · EVENT 카테고리 2종이 전부 대상이라 두 카테고리가 0종이 된다.
--     상점 UI 에 카테고리 탭이 있으면 두 칸이 빈다.
--   * 할인율이 붙은 상품 5종(미드나잇 50 · 네온후드 30 · 골든프레임 30 · 벚꽃 30 ·
--     스타라이트 20)이 전부 대상이라, 남는 12종은 discount_rate 가 모두 0이다.
--
-- 여러 번 실행해도 같은 결과가 된다.

SET NAMES utf8mb4;

-- 실행 전 확인: 이 11종을 누가 갖고 있는지. 지우는 게 아니라 내리는 것이므로 소유자가 있어도
-- 진행에 문제는 없다. 다만 "산 사람이 계속 갖는다"는 것을 눈으로 확인하고 넘어간다.
SELECT s.code, s.name, s.category, s.rarity, s.price, s.discount_rate, s.status,
       COUNT(DISTINCT ui.user_id) AS owners,
       COUNT(DISTINCT p.user_id)  AS equipped
FROM shop_items s
LEFT JOIN user_inventory_items ui ON ui.item_id = s.id
LEFT JOIN user_preferences    p  ON p.equipped_item_id = s.id
WHERE s.code IN ('COSTUME_NEON_HOOD', 'SKIN_GOLDEN_FRAME', 'SKIN_MIDNIGHT', 'COSTUME_STARLIGHT',
                 'COSTUME_CHERRY_SET', 'COSTUME_CLOVER_BADGE', 'SKIN_IVORY_BASIC', 'SKIN_FOREST',
                 'COSTUME_CROBI_PARTY', 'EVENT_SUMMER_NIGHT', 'EVENT_FIRST_SNOW')
GROUP BY s.id
ORDER BY s.sort_order;

-- 판매 종료.
UPDATE shop_items
SET status = 'RETIRED'
WHERE code IN ('COSTUME_NEON_HOOD', 'SKIN_GOLDEN_FRAME', 'SKIN_MIDNIGHT', 'COSTUME_STARLIGHT',
               'COSTUME_CHERRY_SET', 'COSTUME_CLOVER_BADGE', 'SKIN_IVORY_BASIC', 'SKIN_FOREST',
               'COSTUME_CROBI_PARTY', 'EVENT_SUMMER_NIGHT', 'EVENT_FIRST_SNOW');

-- 검증 1: 11행이 전부 RETIRED 여야 한다.
SELECT code, name, status FROM shop_items
WHERE code IN ('COSTUME_NEON_HOOD', 'SKIN_GOLDEN_FRAME', 'SKIN_MIDNIGHT', 'COSTUME_STARLIGHT',
               'COSTUME_CHERRY_SET', 'COSTUME_CLOVER_BADGE', 'SKIN_IVORY_BASIC', 'SKIN_FOREST',
               'COSTUME_CROBI_PARTY', 'EVENT_SUMMER_NIGHT', 'EVENT_FIRST_SNOW')
ORDER BY sort_order;

-- 검증 2: 남은 ACTIVE 는 스킨 12종뿐이어야 한다. image_url 이 전부 /shop/skins/ 로 시작한다.
SELECT code, category, rarity, price, image_url
FROM shop_items WHERE status = 'ACTIVE' ORDER BY sort_order;

-- 검증 3: 판매 중인 카탈로그 총액. ACTIVE 만 세는 것이 실제로 살 수 있는 총액이다.
-- 기대값 34,800 · 완주 (34,800-1,000)/3,000 = 약 12일.
-- ★ 이 값이 나오면 계약 §15-1 을 다시 쓸 것 — 총액과 일수를 같이 고친다.
SELECT COUNT(*) AS active_items,
       SUM(ROUND(price * (100 - discount_rate) / 100)) AS active_catalog_total
FROM shop_items WHERE status = 'ACTIVE';

-- 검증 4: 내린 상품을 이미 산 사람은 그대로 갖고 있어야 한다(0행이면 소유자가 없었던 것).
-- 여기 행이 나오는 것이 정상이다 — RETIRED 는 뺏는 것이 아니다.
SELECT u.id AS user_id, s.code, s.name, ui.paid_price, ui.purchased_at
FROM user_inventory_items ui
JOIN shop_items s ON s.id = ui.item_id
JOIN users u ON u.id = ui.user_id
WHERE s.status = 'RETIRED'
ORDER BY ui.purchased_at DESC;

-- 되돌리기: 마음이 바뀌면 이 한 줄이다.
-- UPDATE shop_items SET status = 'ACTIVE' WHERE code IN ( ...위 11개... );
