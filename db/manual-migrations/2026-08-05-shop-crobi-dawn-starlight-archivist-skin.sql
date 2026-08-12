-- 크로비 스킨 '새벽의 별빛 기록자'를 상점 카탈로그에 등록한다. 마스코트 스킨 14종째다.
--
-- 에셋(9개 상태 PNG + metadata.json)과 Mascot.jsx 배선은 clov-web 쪽에 들어갔다.
-- shop_items 행이 없으면 상점 목록에 아예 안 뜬다 — 카탈로그는 코드가 아니라 이 테이블 데이터다.
--
-- ★ price = 9,800 · LEGENDARY — 아케이드 보스 롭과 같은 정가다. 의도한 것이다.
--   둘 다 LEGENDARY이고 둘 다 상태 9종을 전부 가진 스킨이라, 값을 가르는 두 축(등급·내용)에서
--   완전히 같다. "한정"은 값을 가르는 축이 아니라서 정가를 올리지 않았다.
--
--   LEGENDARY 정가 층: 9,800  아케이드 보스 롭 · 새벽의 별빛 기록자 크로비
--
-- ★★ discount_rate = 80 — 한정 프로모션이다. 실효가 1,960.
--
--   가격 축(price)과 프로모션 축(discount_rate)을 분리했다. 정가가 등급을 말하고
--   할인이 "한정"을 말한다. 이렇게 하면 §15-4의 "등급도 같고 내용도 같으면 값도 같게"가
--   안 깨진다 — 정가를 올려서 한정을 표현했다면 그 규칙을 어겼을 것이다.
--
--   ⚠️ 실효가 1,960 은 EPIC 최고가 6,000 보다 싸다. 프로모션 기간에는 정상이다 —
--      세일이 원래 그렇고, 정가가 등급을 말해준다. 문제가 되는 것은 이 할인이 안 끝날
--      때다. 아래 "해제" 절을 반드시 같이 볼 것.
--
--   ⚠️ 바닥은 90% 다. 9,800 × 10% = 980 > 가장 싼 COMMON 900. 91% 면 882 가 되어
--      최고 등급이 상점에서 제일 싼 물건이 된다. 그 선은 넘지 않는다.
--
--   80% 를 고른 근거가 하나 더 있다. 카탈로그 총액이 52,000 → 53,960 이 되어 완주가
--   (53,960 - 1,000) / 6,000 = 약 9일로, 계약 §15-4 에 적힌 9일이 그대로 맞는다.
--   할인 없이 넣었다면 61,800 / 10일이 되어 계약 숫자를 또 고쳐야 했다.
--
--   체감: 가입 첫날 마스코트 10클릭(200 × 10 = 2,000)이면 닿는다. 시작 골드 1,000 으로는
--   못 사고 하루를 채우면 살 수 있는 자리다 — "누구나 살 수 있되 공짜는 아니다".
--
-- ★ discount_rate 를 실제로 쓰는 첫 상품이다. 계약 §15-3 이 이 필드를 "주간 할인용,
--   운영 주체·주기는 아직 미정"으로 두고 있다(web-design-repository#66). 관리 화면이
--   없어서 끝내려면 SQL 을 직접 돌려야 한다 — 그래서 해제 SQL 을 이 파일 맨 아래에
--   같이 뒀다. 해제 시점을 안 정하면 "한정"이 아니라 그냥 영구 할인이다.
--
-- ★ category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT/BACKGROUND는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도
--   데이터는 COSTUME 이어야 실제로 장착된다.
--
-- ★ image_url 은 기능에 물려 있다. Mascot.jsx 의 EQUIPPED_SKIN_STATE_SPRITES 가 이 경로를
--   endsWith 로 매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태 전환만
--   사라지고 default 한 장짜리 옛 코스튬처럼 조용히 동작한다.
--   폴더명은 dawn-starlight-archivist-legendary 다. metadata.json 의 skinId, Mascot.jsx 의
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
-- 뒤에야 발견했다.
-- ================================================================================
SELECT id, code, name, category, rarity, price, status
FROM shop_items
WHERE code = 'COSTUME_CROBI_DAWN_ARCHIVIST';

-- ================================================================================
-- 등록
-- ================================================================================
INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('COSTUME_CROBI_DAWN_ARCHIVIST', '새벽의 별빛 기록자 크로비',
        '새벽 별빛 아래 추억을 기록하는 크로비의 복장 · 한정',
        'COSTUME', 'LEGENDARY', 9800, 80,
        '/shop/skins/crobi/dawn-starlight-archivist-legendary/default.png', 260)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 한 행. price 9800 · discount_rate 80 · status ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
SELECT id, code, name, category, rarity, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price,
       status, image_url, sort_order
FROM shop_items
WHERE code = 'COSTUME_CROBI_DAWN_ARCHIVIST';

-- 검증 2: LEGENDARY 층. 정가는 둘 다 9,800 이고 실효가는 갈린다.
--   아케이드 보스 롭            9,800  할인 0   → 9,800
--   새벽의 별빛 기록자 크로비   9,800  할인 80  → 1,960   ← 프로모션 중
-- ⚠️ 실효가가 EPIC(6,000)보다 낮은 줄이 있는 것은 프로모션 중이라 정상이다.
--    할인을 해제하면 두 줄 다 9,800 으로 돌아온다.
SELECT rarity, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity = 'LEGENDARY' AND status = 'ACTIVE'
ORDER BY effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 14종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;

-- 검증 4: 판매 중 카탈로그 총액. 아래가 나와야 한다(할인 반영 실효가 기준).
--   skins_total    42,760  (스킨 14종 — 40,800 + 1,960)
--   other_total    11,200  (배경 4종 × RARE 2,800)
--   catalog_total  53,960
--   active_items       18
--
-- ⚠️ 총액이 할인에 물려 있다. 아래 해제 SQL 을 돌리면 61,800 이 되고 완주도 9일 → 10일이
--    된다 — 그때 계약 §15-1·§15-4 의 숫자를 같이 고쳐야 한다.
--
-- ★ 총액을 하나로만 재지 말고 쪼개서 잰다 — 총액 하나로는 어긋난 위치를 못 찾는다.
--
-- ⚠️ other_total 이 NULL 이던 시절의 주석을 그대로 믿지 말 것. 2026-08-04 저녁에 배경 4종이
--    들어오면서 11,200 이 정상이 됐다. 여기에 11,200 이 아닌 값이 뜨면 배경 쪽이 바뀐 것이다.
SELECT
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND image_url LIKE '/shop/skins/%')     AS skins_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND (image_url IS NULL OR image_url NOT LIKE '/shop/skins/%')) AS other_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE')                                        AS catalog_total,
  (SELECT COUNT(*) FROM shop_items WHERE status = 'ACTIVE')          AS active_items;


-- ================================================================================
-- ★★★ 한정 프로모션 해제 — 끝낼 때 이 블록만 실행한다
--
-- ⚠️ 실행하지 말고 남겨두는 블록이다. 아래 주석을 풀어서 쓴다.
--
-- 이 파일에 해제 SQL 을 같이 두는 이유:
--   관리 화면이 없어서 할인을 끝내려면 SQL 을 직접 돌려야 한다. 등록 SQL 만 남기면
--   "어떻게 끝내지"를 나중 사람이 처음부터 만들어야 하고, 그러면 대개 안 끝난다.
--   "한정"이 영구 할인이 되는 경로가 이것이다.
--
-- ⚠️ 해제해도 이미 산 사람의 가격은 안 바뀐다. user_inventory_items.paid_price 가
--    구매 시점 실효가(1,960)를 박아두기 때문이다(계약 §15-1 (4)). 되돌릴 수 없다 —
--    프로모션 중 구매자는 영구히 그 값으로 산 것으로 남는다. 의도된 동작이다.
--
-- 해제 후 확인할 것:
--   1. 실효가가 9,800 으로 돌아왔는지 (검증 1·2 재실행)
--   2. 카탈로그 총액 53,960 → 61,800, 완주 9일 → 10일
--   3. ★ 계약 §15-1 의 총액과 §15-4 의 완주 일수를 같이 고칠 것
--      "총액만 고치고 일수를 두면 그게 다음 판단을 왜곡한다"(§15-4)
-- ================================================================================
-- UPDATE shop_items SET discount_rate = 0 WHERE code = 'COSTUME_CROBI_DAWN_ARCHIVIST';
