-- 오닉스 스킨 '블랙스타 공방'을 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 RGBA + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블
-- 데이터다 — public/shop/skins/_template/README.md 도 "가격과 판매 기간은 에셋 메타데이터가
-- 아니라 서버의 shop_items 데이터에서 관리한다"고 못박고 있다.
--
-- 여섯 번째 스킨이고, 이걸로 마스코트 5종 중 4종이 갖던 스킨이 오닉스까지 5종으로 늘어난다
-- (버거노인만 없다 — 본체 상태 세트가 1/9라 순서가 다르다).
--
-- ★ price = 1,500 — 철골 작업반 김철수(COSTUME_KIM_CHEOLSU_SAFETY)와 같은 값이다. 의도한 것이다.
--
--   두 아이템은 값을 가르는 두 축에서 완전히 같다.
--     등급   둘 다 UNCOMMON
--     내용   둘 다 상태 9종을 전부 가진 스킨
--   같은 것에 다른 값을 붙이려면 근거가 있어야 하는데, 여기엔 없다. 굳이 1,600 같은
--   값을 만들면 그게 바로 "근거 없이 흩어진 숫자"다.
--   같은 값 두 개는 이 카탈로그의 관례이기도 하다 — EPIC의 마지막 신호병·오션 구조대가
--   둘 다 6,000이고, LEGENDARY의 벚꽃 세트·아케이드 보스가 둘 다 9,800이다.
--
--   UNCOMMON 층은 이렇게 된다.
--       960  스타라이트 (1,200의 20% 할인)
--     1,500  포레스트
--     1,500  철골 작업반 · 블랙스타 공방  <- 상태 9종을 가진 것들
--   위 등급을 안 넘는다: 가장 싼 RARE 실효가가 1,000(미드나잇, 50% 할인)이라 정가로는
--   2,000 > 1,500 으로 순서가 맞다. 실효가 기준 검증 쿼리가 이 줄을 FAIL로 잡으면 오탐이다
--   — 할인 때문에 생긴 기존 교차이고, 2026-08-03 핸드오프에 이미 적혀 있다.
--
--   획득 속도는 시작 1,000 + 하루 상한 500(계약 §15-4) 기준 하루면 닿는다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓰면 된다(reason 은 SIGNUP_GRANT — EARN_ 계열을
--   쓰면 하루 상한 500 합산에 잡혀 그날 정상 획득이 막힌다).
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도 데이터는 COSTUME
--   이어야 실제로 장착된다.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라지고
--   default 한 장짜리 옛 코스튬처럼 동작한다.
--   폴더명은 blackstar-atelier-uncommon 이다. metadata.json 과 Mascot.jsx 의
--   EQUIPPED_SKIN_STATE_SPRITES 에 적힌 것과 한 글자도 다르면 안 된다.
--
-- ★ 에셋을 먼저 배포하고 이 SQL을 실행한다. 순서가 반대면 상점에 이름만 뜨고 그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_ONYX_BLACKSTAR', '블랙스타 공방 오닉스', '검은 별 망치를 든 오닉스의 세공사 복장',
        'COSTUME', 'UNCOMMON', 1500, 0,
        '/shop/skins/onyx/blackstar-atelier-uncommon/default.png', 180)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 아래 한 행이 나와야 한다. price 가 1500, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ONYX_BLACKSTAR';

-- 검증 2: UNCOMMON 층 순서 확인. 실효가로 960 · 1,500 · 1,500 · 1,500 이 나와야 한다.
-- 같은 값 세 줄이 정상이다(위 주석 참고).
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity = 'UNCOMMON'
ORDER BY effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 6종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
-- 새 것만 보지 말고 기존 것까지 같이 본다 — 스킨이 늘수록 값이 커지는 쿼리다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;
