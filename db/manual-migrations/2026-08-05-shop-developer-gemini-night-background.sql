-- 한정 배경 '은하수 아래 개발자의 밤'을 상점 카탈로그에 등록한다. 배경 5종째다.
--
-- 에셋(1920x1080 WebP 배경 + 512x512 PNG 썸네일 + 상점 카드)과 appBackground.js 배선,
-- CSS 별 반짝임 레이어는 clov-web 쪽에 들어갔다.
--
-- ★★ rarity = LEGENDARY 인 근거는 "한정"이 아니라 "CSS 효과 레이어"다.
--
--   기존 배경 4종은 전부 RARE 2,800 이다. 이 배경만 등급이 다르면 무엇이 다른지를
--   적어야 한다 — 계약 §15-4 의 기준이 "등급도 같고 내용도 같으면 값도 같게"이기 때문이다.
--
--     사계절 4종   정지 이미지 한 장                     RARE      2,800
--     이 배경      정지 이미지 + 다른 배경엔 없는 효과    LEGENDARY 9,800
--
--   "내용이 다르다"가 등급을 가르는 정당한 축이다.
--
--   ⚠️ "한정이라서 비싸다"로 읽지 말 것. 한정은 아래 discount_rate 로 이미 표현하고
--      있어서, 등급 근거로도 쓰면 같은 이유를 두 축에 쓰는 것이 된다. 그러면 다음
--      한정 상품에서 "그럼 이것도 등급을 올려야 하나"가 근거 없이 반복된다.
--
--   ★ 그래서 조건이 붙는다 — clov-web 의 별 반짝임 레이어
--     (index.css 의 [data-app-bg="developer-gemini-night"])를 빼면 이 등급의 근거가
--     사라진다. 그때는 RARE 2,800 으로 내리는 것이 맞다.
--
-- ★ price = 9,800 은 LEGENDARY 층 규칙 그대로다. 같은 층에 아케이드 보스 롭과
--   새벽의 별빛 기록자 크로비가 있다. 새 가격대를 만들지 않는다.
--
-- ★★ discount_rate = 80 — 한정 프로모션이다. 실효가 1,960.
--   같은 날 등록한 크로비 한정 스킨과 실효가가 같다. 둘이 "한정 세트"로 묶인다.
--
--   가격 축(price)과 프로모션 축(discount_rate)을 분리했다. 정가가 등급을 말하고
--   할인이 "한정"을 말한다. 정가를 올려서 한정을 표현했다면 §15-4 를 어겼을 것이다.
--
--   ⚠️ 실효가 1,960 은 EPIC 최고가 6,000 보다 싸다. 프로모션 기간에는 정상이다.
--      문제가 되는 것은 이 할인이 안 끝날 때다 — 아래 "해제" 절을 반드시 같이 볼 것.
--
--   ⚠️ 바닥은 90% 다. 9,800 x 10% = 980 > 가장 싼 COMMON 900. 91% 면 882 가 되어
--      최고 등급이 상점에서 제일 싼 물건이 된다.
--
-- ★ category = 'BACKGROUND' 다. 'SKIN' 도 'COSTUME' 도 아니다.
--   배경은 장착(equip)을 안 쓴다 — 선택값은 서버가 아니라 기기-로컬
--   (localStorage 'clov_appBgTheme')에 저장된다. equip 을 부르면 COSTUME 이 아니라서
--   409 ITEM_NOT_EQUIPPABLE 이 맞게 떨어진다(계약 §15-3).
--   상점이 답하는 건 "고를 수 있는가"(소유)까지고, "무엇을 골랐는가"는 기기가 갖는다.
--
-- ★ code 는 화면 잠금에 물려 있다. appBackground.js 의 APP_BACKGROUNDS 항목에 있는
--   itemCode 와 한 글자도 다르면 안 된다 — 다르면 산 사람도 영원히 잠긴 채로 보이고,
--   에러가 안 나서 조용하다. isBackgroundUnlocked(bg, ownedCodes) 가 이 값을 대조한다.
--
--   ⚠️ image_url 로 소유를 대조하는 우회를 만들지 말 것. 썸네일 경로를 바꾸는 순간
--      이미 산 사람의 소유가 풀린다(계약 §15-3). 2026-08-04 에 배경 카드 그림을 실제
--      그림으로 바꾸는 UPDATE 를 돌렸는데, imageUrl 대조를 쓰고 있었으면 그 한 줄로
--      배경 4종이 전부 잠겼다.
--
-- ★ 에셋을 먼저 배포하고 이 SQL 을 실행한다. 순서가 반대면 상점에 이름만 뜨고
--   그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT 로 쓴다.

SET NAMES utf8mb4;

-- ================================================================================
-- 사전 점검: code 충돌 확인. ★ 반드시 INSERT 전에 따로 실행한다.
--
-- 0행이 나와야 한다. 1행이 나오면 그 아래 INSERT 를 실행하지 말 것 —
-- ON DUPLICATE KEY UPDATE 가 기존 상품을 조용히 덮어쓰고, 덮어쓴 뒤에는 검증 쿼리로
-- 구분할 방법이 없다("1행 반환"이 신규든 덮어쓰기든 똑같이 나온다).
--
-- 2026-08-04 에 실제로 당했다: COSTUME_ROB_EXPLORER 를 UNCOMMON 1,500 으로 넣었는데
-- 같은 code 의 EPIC 5,200 시드가 있어서 통째로 덮였고, 카탈로그 총액이 5,200 어긋난
-- 뒤에야 발견했다.
--
-- ⚠️ 다만 INSERT 의 affected rows 로는 구분된다 — 신규면 1, 덮어쓰기면 2 다.
-- ================================================================================
SELECT id, code, name, category, rarity, price, status
FROM shop_items
WHERE code = 'BACKGROUND_DEVELOPER_GEMINI_NIGHT';

-- ================================================================================
-- 등록
-- ================================================================================
INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES ('BACKGROUND_DEVELOPER_GEMINI_NIGHT', '은하수 아래 개발자의 밤',
        '은하수와 쌍둥이자리가 뜬 산장 데크의 밤 · 한정',
        'BACKGROUND', 'LEGENDARY', 9800, 80,
        '/shop/backgrounds/developer-gemini-night.webp', 340)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 한 행. price 9800 · discount_rate 80 · effective_price 1960 · status ACTIVE.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
SELECT id, code, name, category, rarity, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price,
       status, image_url, sort_order
FROM shop_items
WHERE code = 'BACKGROUND_DEVELOPER_GEMINI_NIGHT';

-- 검증 2: LEGENDARY 층. 세 줄이 나와야 한다.
--   아케이드 보스 롭            9,800  할인 0   → 9,800
--   새벽의 별빛 기록자 크로비   9,800  할인 80  → 1,960
--   은하수 아래 개발자의 밤     9,800  할인 80  → 1,960
-- ⚠️ 실효가가 EPIC(6,000)보다 낮은 줄이 둘 있는 것은 프로모션 중이라 정상이다.
SELECT rarity, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity = 'LEGENDARY' AND status = 'ACTIVE'
ORDER BY effective_price;

-- 검증 3: 배경 전수 점검. 이제 5종이다.
-- category 가 전부 BACKGROUND 여야 한다. 여기 COSTUME 이 섞이면 장착 가능해져서
-- "배경인데 마스코트에 씌워지는" 상태가 된다.
SELECT code, rarity, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price,
       category, status,
       IF(category = 'BACKGROUND', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE category = 'BACKGROUND' OR image_url LIKE '/shop/backgrounds/%'
ORDER BY sort_order;

-- 검증 4: 판매 중 카탈로그 총액(할인 반영 실효가 기준). 아래가 나와야 한다.
--   skins_total    42,760  (마스코트 스킨 14종)
--   other_total    13,160  (배경 5종 — 11,200 + 1,960)
--   catalog_total  55,920
--   active_items       19
--
-- ★ 총액을 하나로만 재지 말고 쪼개서 잰다 — 총액 하나로는 어긋난 위치를 못 찾는다.
SELECT
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND image_url LIKE '/shop/skins/%')     AS skins_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE' AND (image_url IS NULL OR image_url NOT LIKE '/shop/skins/%')) AS other_total,
  (SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) FROM shop_items
     WHERE status = 'ACTIVE')                                        AS catalog_total,
  (SELECT COUNT(*) FROM shop_items WHERE status = 'ACTIVE')          AS active_items;

-- ★ 완주 일수는 안 바뀐다. (55,920 - 1,000) / 6,000 = 약 9일로 계약 §15-4 에 적힌
--   9일이 그대로 맞는다. 할인 없이 넣었다면 65,720 / 약 11일이 되어 계약을 고쳐야 했다.
--   총액이 늘었는데 일수가 그대로인 것을 이상하게 여기지 말 것 — 실효가로 재기 때문이다.

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
--    구매 시점 실효가(1,960)를 박아두기 때문이다(계약 §15-1 (4)). 되돌릴 수 없다.
--
-- 해제 후 확인할 것:
--   1. 실효가가 9,800 으로 돌아왔는지 (검증 1·2 재실행)
--   2. 카탈로그 총액 55,920 → 63,760, 완주 9일 → 약 11일
--   3. ★ 계약 §15-1 의 총액과 §15-4 의 완주 일수를 같이 고칠 것
--      "총액만 고치고 일수를 두면 그게 다음 판단을 왜곡한다"(§15-4)
-- ================================================================================
-- UPDATE shop_items SET discount_rate = 0 WHERE code = 'BACKGROUND_DEVELOPER_GEMINI_NIGHT';
