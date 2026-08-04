-- 타코군 스킨 '수박껍질모자'를 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 RGBA + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블 데이터다.
--
-- 타코군의 두 번째 스킨이고(ROV 조종사 RARE), 스킨 전체로는 여덟 번째다.
--
-- ★ price = 900 — 옐로우 크루 롭(COSTUME_ROB_YELLOW_CREW)과 같은 값이다. 의도한 것이다.
--
--   두 아이템은 값을 가르는 두 축에서 완전히 같다.
--     등급   둘 다 COMMON
--     내용   둘 다 상태 9종을 전부 가진 스킨
--   같은 것에 다른 값을 붙이려면 근거가 있어야 하는데 여기엔 없다.
--
--   같은 값 두 개는 이 카탈로그의 관례다 — UNCOMMON의 철골 작업반·블랙스타 공방이 둘 다
--   1,500, EPIC의 마지막 신호병·오션 구조대가 둘 다 6,000, LEGENDARY의 벚꽃 세트·
--   아케이드 보스가 둘 다 9,800이다. 이제 COMMON에도 같은 쌍이 생긴다.
--
--   COMMON 층은 이렇게 된다.
--       500  아이보리 기본 (SKIN · 장착 불가한 옛 아이템)
--       600  클로버 배지
--       900  옐로우 크루 · 수박껍질모자   <- 상태 9종을 가진 것들
--   위 등급을 안 넘는다: UNCOMMON 최저 실효가가 960(스타라이트 1,200의 20% 할인)이다.
--
--   ★ 900은 시작 골드 1,000 아래다. 가입 첫날 바로 살 수 있는 층이고, 이제 그 층에
--     캐릭터가 둘(롭·타코군)이 된다 — 첫 스킨 선택지가 생긴다는 뜻이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓴다(reason 은 SIGNUP_GRANT — EARN_ 계열을 쓰면
--   하루 상한 500 합산에 잡혀 그날 정상 획득이 막힌다).
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). 같은 COMMON 층의 'SKIN_IVORY_BASIC'이
--   category='SKIN'인데 그건 장착이 안 되는 옛 아이템이다. 따라 하지 말 것.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라진다.
--   폴더명은 watermelon-rind-hat-common 이다. rind(껍질)를 rand/rine 으로 적지 않도록 주의.
--
-- ★ 에셋을 먼저 배포하고 이 SQL을 실행한다. 순서가 반대면 상점에 이름만 뜨고 그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_TAKO_GUN_WATERMELON', '수박껍질모자 타코군', '수박 껍질을 모자로 눌러쓴 여름 타코군',
        'COSTUME', 'COMMON', 900, 0,
        '/shop/skins/tako-gun/watermelon-rind-hat-common/default.png', 200)
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
WHERE code = 'COSTUME_TAKO_GUN_WATERMELON';

-- 검증 2: COMMON~UNCOMMON 층 경계 확인. 실효가로
-- 500 · 600 · 900 · 900 · 960 · 1500 · 1500 · 1500 순이 나와야 한다.
-- COMMON 에 900 이 두 줄인 것이 정상이다(위 주석 참고).
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('COMMON', 'UNCOMMON')
ORDER BY FIELD(rarity, 'COMMON', 'UNCOMMON'), effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 8종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
-- 새 것만 보지 말고 기존 것까지 같이 본다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;
