-- 타코군 레어 스킨 'ROV 조종사'를 상점 카탈로그에 등록한다.
--
-- 배경: 에셋(9개 상태 PNG 640x640 + metadata.json)과 프론트 배선은 clov-web에 들어갔는데
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는 코드가 아니라 이 테이블
-- 데이터다. 오늘 네 번째 스킨이고, 마스코트로는 롭·김철수에 이어 세 번째다.
--
-- price = 2,800 (할인 0%). metadata.json 의 rarity 가 RARE 다. 지금 카탈로그를 실효가
--   (price * (100 - discount_rate) / 100) 로 줄 세우면 이 아이템이 들어갈 창이 좁다.
--
--     RARE   미드나잇 1,000(2,000의 50% 할인) · 네온 후드 1,680(2,400의 30% 할인)
--            크로비 파티모자 2,200
--     EPIC   골든 프레임 3,360(4,800의 30% 할인) · 롭 탐험가 5,200
--            마지막 신호병 6,000 · 첫눈 6,400(EVENT)
--
--   2,800 은 상시 RARE 최고가(크로비 파티모자 2,200) 위, 가장 싼 EPIC 실효가
--   (골든 프레임 3,360) 아래다. 등급이 교차하지 않는다.
--   ★ RARE 에는 EVENT 아이템이 없다. 그래서 마지막 신호병(EPIC) 때와 달리
--     "기간 한정이 상시보다 비싸야 한다"는 제약이 이 층에는 걸리지 않는다.
--   ★ 미드나잇 1,000 이 UNCOMMON 포레스트 1,500 보다 싼 것은 50% 할인 때문이다.
--     기존 데이터의 교차라 여기서 건드리지 않는다 — 정가로는 순서가 맞다.
--   획득 속도는 시작 1,000 + 하루 상한 500(계약 §15-4) 기준 약 4일이다.
--   김철수 1,500(1일) 다음 계단이고, 마지막 신호병 6,000(10일)·아케이드 보스
--   9,800(18일)로 이어진다. 등급마다 한 단계씩 멀어지도록 잡은 값이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓰면 된다.
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도 데이터는 COSTUME
--   이어야 실제로 장착된다.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 equippedSkinStates 가 이 경로를 endsWith 로
--   매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만 사라지고
--   default 한 장짜리 옛 코스튬처럼 동작한다.
--   폴더명이 'rob-'이 아니라 'rov-'다(ROV = 원격 조종 잠수정). 오타로 보고 고치지 말 것.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_TAKO_GUN_ROV_PILOT', 'ROV 조종사 타코군', '심해 탐사정을 모는 타코군',
        'COSTUME', 'RARE', 2800, 0,
        '/shop/skins/tako-gun/rov-pilot-rare/default.png', 160)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 아래 한 행이 나와야 한다. price 가 2800, status 가 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
-- 08-02에 COMMIT 이 초록인데 데이터가 안 바뀌어 있던 적이 있어 실행 후 반드시 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_TAKO_GUN_ROV_PILOT';

-- 검증 2: 등급 순서가 안 뒤집혔는지 실효가로 확인한다. RARE 가 전부 EPIC 아래,
-- EPIC 이 전부 LEGENDARY 아래여야 한다(할인으로 인한 기존 교차는 위 주석 참고).
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('RARE', 'EPIC', 'LEGENDARY')
ORDER BY FIELD(rarity, 'RARE', 'EPIC', 'LEGENDARY'), effective_price;

-- 검증 3: 마스코트 스킨 4종이 다 같은 규칙을 지키는지 한 번에 본다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;
