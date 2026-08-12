-- 롭 에픽 스킨 '마지막 신호병'을 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블
-- 데이터다. 같은 마스코트의 두 번째 스킨이라 아케이드 보스(COSTUME_ROB_ARCADE_BOSS)와
-- 나란히 놓인다 — 등급이 갈리므로 가격도 갈려야 한다.
--
-- price = 6,000 (할인 0%). metadata.json 의 rarity 가 EPIC 이고, 지금 카탈로그의 실효가는
--   이렇게 줄 서 있다:
--     EPIC   골든 프레임 3,360(4,800의 30% 할인) · 롭 탐험가 5,200 · 첫눈 6,400(EVENT)
--     LEGEND 벚꽃 세트 6,860(9,800의 30% 할인) · 아케이드 보스 9,800 · 여름밤 12,000(EVENT)
--   6,000 은 상시 판매 EPIC 최고가(5,200) 위, 기간 한정 EPIC(6,400) 아래, 그리고 가장 싼
--   LEGENDARY 실효가(6,860) 아래다. 어느 선도 넘지 않아 등급 순서가 유지된다.
--   ★ EVENT 가 같은 등급의 상시 상품보다 비싼 건 기존 카탈로그가 이미 지킨 규칙이다
--     (여름밤 12,000 > 아케이드 보스 9,800). 그 규칙을 EPIC 층에서도 그대로 따랐다.
--   획득 속도는 시작 1,000 + 하루 상한 500(계약 §15-4) 기준 10일, 교감만 하면 하루 300이라
--   약 17일이다. 아케이드 보스(18일/30일)의 절반 — 한 등급 아래로 의도한 값이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   가격을 내리면 위 순서가 무너지고, 되돌리기 전에 산 계정은
--   user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓰면 된다.
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도 데이터는 COSTUME
--   이어야 실제로 장착된다.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라지고
--   default 한 장짜리 옛 코스튬처럼 동작한다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_ROB_LAST_SIGNAL', '마지막 신호병 Rob', '교신이 끊긴 밤, 마지막 신호를 보내는 롭',
        'COSTUME', 'EPIC', 6000, 0,
        '/shop/skins/rob/last-signal-epic/default.png', 150)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 아래 한 행이 나와야 한다. price 가 6000, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
-- 08-02에 COMMIT 이 초록인데 데이터가 안 바뀌어 있던 적이 있어 실행 후 반드시 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ROB_LAST_SIGNAL';

-- 검증 2: 등급 순서가 안 뒤집혔는지 실효가로 확인한다. 위에서 아래로 값이 커져야 하고,
-- EPIC 세 줄이 전부 LEGENDARY 첫 줄보다 싸야 한다.
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('EPIC', 'LEGENDARY')
ORDER BY effective_price;
