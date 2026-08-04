-- 오닉스 스킨 '개발 팀장'을 상점 카탈로그에 등록한다. 마스코트 스킨 13종째다.
--
-- 에셋(9개 상태 PNG 640x640 + metadata.json)과 Mascot.jsx 배선은 clov-web 쪽에 들어갔다.
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다 — 카탈로그는 코드가 아니라 이 테이블 데이터다.
--
-- ★ price = 6,000 · EPIC — 마지막 신호병 롭 · 오션 구조대 크로비와 같은 값이다. 의도한 것이다.
--   셋 다 EPIC이고 셋 다 상태 9종을 전부 가진 스킨이라, 값을 가르는 두 축에서 완전히 같다.
--   같은 것에 다른 값을 붙이려면 근거가 있어야 하는데 여기엔 없다. 6,200 같은 값을 새로
--   만들면 그게 바로 "근거 없이 흩어진 숫자"다.
--
--   EPIC 층은 이렇게 된다(할인 없음).
--     6,000  마지막 신호병 롭 · 오션 구조대 크로비 · 개발 팀장 오닉스
--   위아래 등급을 안 넘는다: RARE 최고가 2,800 < 6,000 < LEGENDARY 9,800.
--
--   획득 속도는 시작 1,000 + 하루 상한 3,000(계약 §15-4) 기준 이틀이면 닿는다.
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT/BACKGROUND는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도
--   데이터는 COSTUME 이어야 실제로 장착된다.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 EQUIPPED_SKIN_STATE_SPRITES 가 이 경로를
--   endsWith 로 매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만
--   사라지고 default 한 장짜리 옛 코스튬처럼 조용히 동작한다.
--   폴더명은 development-team-lead-epic 이다. metadata.json 의 skinId, Mascot.jsx 의
--   배열 항목과 한 글자도 다르면 안 된다.
--
-- ★ 에셋을 먼저 배포하고 이 SQL을 실행한다. 순서가 반대면 상점에 이름만 뜨고 그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

-- ================================================================================
-- 사전 점검: code 충돌 확인. ★ 반드시 INSERT 전에 따로 실행한다.
--
-- 0행이 나와야 한다. 1행이 나오면 그 아래 INSERT 를 실행하지 말 것 —
-- ON DUPLICATE KEY UPDATE 가 기존 상품을 조용히 덮어쓰고, 덮어쓴 뒤에는 검증 쿼리로
-- 구분할 방법이 없다("1행 반환"이 신규든 덮어쓰기든 똑같이 나온다).
--
-- 2026-08-04에 실제로 당했다: COSTUME_ROB_EXPLORER 를 UNCOMMON 1,500 으로 넣었는데
-- 같은 code 의 EPIC 5,200 시드가 있어서 통째로 덮였고, 카탈로그 총액이 5,200 어긋난
-- 뒤에야 발견했다. 총액을 스킨/시드로 쪼개서 재고 나서야 위치를 잡을 수 있었다.
-- ================================================================================
SELECT id, code, name, category, rarity, price, status
FROM shop_items
WHERE code = 'COSTUME_ONYX_DEV_LEAD';

-- ================================================================================
-- 등록
-- ================================================================================
INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_ONYX_DEV_LEAD', '개발 팀장 오닉스', '팀을 이끄는 오닉스의 개발 팀장 복장',
        'COSTUME', 'EPIC', 6000, 0,
        '/shop/skins/onyx/development-team-lead-epic/default.png', 250)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 한 행. price 6000, status ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_ONYX_DEV_LEAD';

-- 검증 2: EPIC 층 순서. 실효가 6,000 이 세 줄 나와야 한다. 같은 값 세 줄이 정상이다.
SELECT rarity, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity = 'EPIC' AND status = 'ACTIVE'
ORDER BY effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 13종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;

-- 검증 4: 판매 중 카탈로그 총액. 40,800 / 13종이 나와야 한다(배경 4종 등록 전 기준).
-- ★ 총액을 하나로만 재지 말고 쪼개서 잰다 — 총액 하나로는 어긋난 위치를 못 찾는다.
-- other_total 은 NULL 이 정상이다: 2026-08-04에 자리표시 11종을 RETIRED 로 내려서
-- 판매 중인 상품이 전부 마스코트 스킨이 됐다. 여기에 숫자가 뜨면 내린 게 살아난 것이다.
SELECT
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND image_url LIKE '/shop/skins/%')     AS skins_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND (image_url IS NULL OR image_url NOT LIKE '/shop/skins/%')) AS other_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE')                                        AS catalog_total,
  (SELECT COUNT(*) FROM shop_items WHERE status = 'ACTIVE')          AS active_items;
