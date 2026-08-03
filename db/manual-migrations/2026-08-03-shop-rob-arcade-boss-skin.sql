-- 롭 전설 스킨 '아케이드 게임 보스'를 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 이미 들어갔는데
-- shop_items 행이 없어서 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블 데이터라
-- 여기서 넣어야 화면에 나온다.
--
-- price = 9,800 (할인 0%). 임시값 7에서 정가로 확정했다(리더 결정 2026-08-03).
--   같은 LEGENDARY 실효가가 6,860(벚꽃 세트 9,800의 30% 할인)~12,000(여름밤)이라 그 안에 든다.
--   여름밤 12,000은 EVENT(기간 한정)라 최고가 자리를 지키게 두고, 이 아이템이 상시 판매
--   중 최고가가 된다 — 기존 COSTUME 최고 정가인 벚꽃 세트와 같은 선이다.
--   등급 대비 값어치도 맞다: 다른 11종은 SVG 한 장인데 이건 상태 9종을 다 가진 유일한
--   아이템이라, 장착하면 연타·수면·드래그가 전부 스킨 그림으로 바뀐다.
--   획득 속도는 시작 1,000 + 하루 상한 500(계약 §15-4) 기준 약 18일, 교감만 하면
--   하루 300이라 30일이다. 전설 등급의 장기 목표로 의도한 값이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   가격을 내리면 카탈로그 등급 체계가 무너지고, 되돌리기 전에 산 계정은
--   user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/ 의 지갑 스크립트를 쓰면 된다.
--
-- category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을 허용한다
-- (SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 부르더라도 데이터는 COSTUME이어야
-- 실제로 장착된다. clov-web public/shop/skins/_template/README.md 규칙과 같다.
--
-- image_url 은 clov-web 정적 자원 경로다. Mascot.jsx 가 이 경로로 9개 상태를 찾으므로
-- (equippedSkinStates 가 defaultPath 로 endsWith 매칭) 오타가 나면 스킨이 상태별로 안 바뀌고
-- 단일 이미지 코스튬처럼 한 장만 나온다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_ROB_ARCADE_BOSS', '아케이드 게임 보스', '롭이 아케이드 보스로 변신하는 전설 스킨',
        'COSTUME', 'LEGENDARY', 9800, 0, '/shop/skins/rob/arcade-boss-legendary/default.png', 130)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증: 아래 한 행이 나와야 한다. price 가 9800, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — 즉 "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
-- 08-02에 COMMIT 이 초록인데 데이터가 안 바뀌어 있던 적이 있어 실행 후 반드시 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ROB_ARCADE_BOSS';
