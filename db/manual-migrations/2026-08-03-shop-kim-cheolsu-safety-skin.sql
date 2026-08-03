-- 김철수 스킨 '철골 작업반'을 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블
-- 데이터다 — public/shop/skins/_template/README.md 도 "가격과 판매 기간은 에셋 메타데이터가
-- 아니라 서버의 shop_items 데이터에서 관리한다"고 못박고 있다.
--
-- price = 1,500 (할인 0%). metadata.json 의 rarity 가 UNCOMMON 이고, 기존 UNCOMMON 이
--   COSTUME_STARLIGHT 1,200(20% 할인 → 실효 960)과 SKIN_FOREST 1,500이다. 상태 9종을
--   다 가진 스킨이라 등급 안에서 위쪽인 1,500에 맞췄다.
--   시작 골드 1,000 + 하루 상한 500(계약 §15-4) 기준 이틀이면 닿는다 — 첫 스킨으로
--   의도한 값이다. 전설 등급인 아케이드 보스(9,800)와 층이 갈린다.
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
VALUES ('COSTUME_KIM_CHEOLSU_SAFETY', '철골 작업반 김철수', '안전모와 작업복으로 무장한 김철수',
        'COSTUME', 'UNCOMMON', 1500, 0,
        '/shop/skins/kim-cheolsu/steel-frame-safety-uncommon/default.png', 140)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증: 아래 한 행이 나와야 한다. price 가 1500, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
-- 08-02에 COMMIT 이 초록인데 데이터가 안 바뀌어 있던 적이 있어 실행 후 반드시 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_KIM_CHEOLSU_SAFETY';
