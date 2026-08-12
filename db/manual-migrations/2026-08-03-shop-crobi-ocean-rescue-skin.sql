-- 크로비 에픽 스킨 '오션 구조대 챔피언'을 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 오늘 다섯 번째 스킨이고, 이걸로
-- 마스코트 5종 중 4종(rob·kim-cheolsu·tako-gun·crobi)이 스킨을 갖는다.
--
-- ★ price = 6,000 — 마지막 신호병(COSTUME_ROB_LAST_SIGNAL)과 같은 값이다. 의도한 것이다.
--
--   두 아이템은 값을 가르는 두 축에서 완전히 같다.
--     등급   둘 다 EPIC
--     내용   둘 다 상태 9종을 전부 가진 스킨
--   같은 것에 다른 값을 붙이려면 근거가 있어야 하는데, 여기엔 없다. 굳이 6,200 같은
--   값을 만들면 그게 바로 "근거 없이 흩어진 숫자"다.
--
--   기존 카탈로그에도 같은 값 두 개가 이미 있다 — 벚꽃 세트와 아케이드 보스가 둘 다
--   9,800(LEGENDARY)이다. 등급이 같으면 값이 같은 게 이 카탈로그의 규칙에 맞다.
--
--   EPIC 층은 이렇게 된다.
--     3,360  골든 프레임 (4,800의 30% 할인)
--     5,200  롭 탐험가
--     6,000  마지막 신호병 · 오션 구조대  <- 상태 9종을 가진 것들
--     6,400  첫눈 (EVENT · 기간 한정)
--   여전히 위아래 등급을 안 넘는다: 가장 싼 LEGENDARY 실효가가 6,860(벚꽃 세트)이다.
--
--   획득 속도는 시작 1,000 + 하루 상한 500(계약 §15-4) 기준 약 10일이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓰면 된다.
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE).
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_CROBI_OCEAN_RESCUE', '오션 구조대 챔피언 크로비', '해변을 지키는 크로비의 구조대 복장',
        'COSTUME', 'EPIC', 6000, 0,
        '/shop/skins/crobi/ocean-rescue-champion-epic/default.png', 170)
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
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_CROBI_OCEAN_RESCUE';

-- 검증 2: 등급 순서가 안 뒤집혔는지 실효가로 확인한다.
-- EPIC 에 6,000 이 두 줄 나오는 것이 정상이다(위 주석 참고).
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('RARE', 'EPIC', 'LEGENDARY')
ORDER BY FIELD(rarity, 'RARE', 'EPIC', 'LEGENDARY'), effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 5종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;
