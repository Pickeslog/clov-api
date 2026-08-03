-- 롭 전설 스킨 '아케이드 게임 보스'를 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 이미 들어갔는데
-- shop_items 행이 없어서 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블 데이터라
-- 여기서 넣어야 화면에 나온다.
--
-- ★ price = 7 은 임시값이다(리더 지시 2026-08-03). 시작 골드가 1,000이라 사실상 무료로
--   구매·장착 테스트를 하려는 값이고, 데모 전에 정상가로 올려야 한다. 같은 등급인
--   COSTUME_CHERRY_SET(LEGENDARY)이 9,800, EVENT_SUMMER_NIGHT(LEGENDARY)이 12,000이다.
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
        'COSTUME', 'LEGENDARY', 7, 0, '/shop/skins/rob/arcade-boss-legendary/default.png', 130)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증: 아래 한 행이 나와야 한다. price 가 7이 아니거나 status 가 ACTIVE 가 아니면 상점에서
-- 안 보이거나 못 산다. 08-02에 COMMIT 이 초록인데 데이터가 안 바뀌어 있던 적이 있어
-- 실행 후 반드시 눈으로 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ROB_ARCADE_BOSS';
