-- 롭 스킨 '옐로우 크루'를 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 RGBA + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블 데이터다.
--
-- 롭의 세 번째 스킨이고(아케이드 보스 LEGENDARY · 마지막 신호병 EPIC), 스킨 전체로는 일곱 번째다.
--
-- ★ price = 900 — COMMON 등급에 스킨이 들어오는 것은 처음이라 이 층에 새 최고가가 생긴다.
--
--   기존 COMMON 두 줄은 장식 하나짜리 아이템이다.
--       500  아이보리 기본 스킨 (SKIN)
--       600  클로버 배지 코스튬 (COSTUME)
--   이건 상태 9종을 전부 가진 스킨이라 내용이 다르다. 같은 값을 붙일 근거가 없다.
--
--   등급 안에서 위로 붙이되 위 등급을 넘지 않는다는 규칙을 그대로 쓴다.
--       COMMON     500  아이보리   600  클로버배지   [900 옐로우 크루]
--       UNCOMMON   960  스타라이트(1,200의 20% 할인)  1,500  포레스트·철골작업반·블랙스타공방
--   900 < 960 이라 UNCOMMON 최저 실효가를 안 넘는다.
--
--   스킨이 등급 안에 새 최고가를 만드는 것은 선례가 있다 — ROV 조종사(RARE 2,800)가 기존
--   RARE 최고 2,400을 넘었고, 마지막 신호병(EPIC 6,000)이 기존 EPIC 최고 5,200을 넘었다.
--   철골 작업반(UNCOMMON 1,500)만 기존 최고와 같은 값이었는데, 그건 그 자리에 이미
--   1,500(포레스트)이 있었기 때문이다.
--
--   ★ 900은 시작 골드 1,000 아래다. 가입 첫날 바로 살 수 있는 유일한 스킨이 된다
--     (계약 §15-4: 시작 1,000 + 하루 상한 500). COMMON 층의 입문용 스킨으로 의도한 값이다.
--     블랙스타 공방(UNCOMMON 1,500)은 하루, ROV 조종사(RARE 2,800)는 나흘이 걸린다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓴다(reason 은 SIGNUP_GRANT — EARN_ 계열을 쓰면
--   하루 상한 500 합산에 잡혀 그날 정상 획득이 막힌다).
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). 같은 COMMON 층의 'SKIN_IVORY_BASIC'이
--   category='SKIN'인데, 그건 장착이 안 되는 옛 아이템이다. 따라 하지 말 것.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라진다.
--
-- ★ 에셋을 먼저 배포하고 이 SQL을 실행한다. 순서가 반대면 상점에 이름만 뜨고 그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_ROB_YELLOW_CREW', '옐로우 크루 롭', '노란 작업복에 파란 멜빵을 맨 현장 크루 롭',
        'COSTUME', 'COMMON', 900, 0,
        '/shop/skins/rob/yellow-crew-common/default.png', 190)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 아래 한 행이 나와야 한다. price 가 900, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ROB_YELLOW_CREW';

-- 검증 2: COMMON~UNCOMMON 층 경계가 안 뒤집혔는지 실효가로 확인한다.
-- 500 · 600 · 900 · 960 · 1500 · 1500 · 1500 순으로 나와야 한다.
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('COMMON', 'UNCOMMON')
ORDER BY FIELD(rarity, 'COMMON', 'UNCOMMON'), effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 7종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
-- 새 것만 보지 말고 기존 것까지 같이 본다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;
