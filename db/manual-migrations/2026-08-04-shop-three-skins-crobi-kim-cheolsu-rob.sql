-- 스킨 3종을 상점 카탈로그에 한 번에 등록한다.
--   크로비 '마법학교 학생'   COMMON    900
--   김철수 '신입 개발자'     UNCOMMON  1,500
--   롭     '탐험가'          UNCOMMON  1,500
--
-- 배경: 에셋(9개 상태 PNG 640x640 RGBA + metadata.json)과 프론트 배선(Mascot.jsx)은
-- clov-web에 들어갔는데 shop_items 행이 없으면 상점 목록에 아예 안 뜬다. 카탈로그는
-- 코드가 아니라 이 테이블 데이터다.
--
-- 세 개를 한 파일에 묶은 것은 같은 배치라 근거·검증 쿼리가 공유되기 때문이다. 스킨마다
-- 파일을 나누던 기존 관례에서 벗어나지만, 실행하는 쪽은 한 번만 붙여넣으면 된다.
--
-- 이걸로 스킨이 8종 → 11종이 되고, 마스코트 6종 중 5종이 스킨을 갖는 것은 그대로다
-- (버거노인만 없다 — 본체 상태 세트가 1/9라 순서가 다르다). 대신 크로비·롭·김철수가
-- 각각 스킨을 하나씩 더 갖게 된다.
--
-- ══════════════════════════════════════════════════════════════════════
-- ★ 가격 — 등급도 같고 내용도 같으면 값도 같게 둔다
-- ══════════════════════════════════════════════════════════════════════
--
-- 세 개 다 상태 9종을 전부 가진 스킨이다. 그러면 값을 가르는 축은 등급 하나만 남는다.
--
--   COMMON     900  옐로우 크루 · 수박껍질모자 · [마법학교 학생]
--   UNCOMMON 1,500  철골 작업반 · 블랙스타 공방 · [신입 개발자] · [탐험가]
--
-- 같은 것에 다른 값을 붙이려면 근거가 있어야 하는데 여기엔 없다. 굳이 950이나 1,600을
-- 만들면 그게 바로 "근거 없이 흩어진 숫자"다. 같은 값 여러 줄은 이 카탈로그의 관례이기도
-- 하다 — EPIC의 마지막 신호병·오션 구조대가 둘 다 6,000, LEGENDARY의 벚꽃 세트·아케이드
-- 보스가 둘 다 9,800이다.
--
-- 위아래 등급을 안 넘는지 확인했다.
--   COMMON 층    500 아이보리 · 600 클로버 배지 · 900 (상태 9종짜리들)
--                → 위 등급 최저 실효가 960(스타라이트, 1,200의 20% 할인)을 안 넘는다
--   UNCOMMON 층  960 스타라이트 · 1,500 포레스트 · 1,500 (상태 9종짜리들)
--                → 가장 싼 RARE 실효가가 1,000(미드나잇, 50% 할인)이라 정가로는
--                  2,000 > 1,500 으로 순서가 맞다. 실효가 기준 검증 쿼리가 이 줄을
--                  FAIL로 잡으면 오탐이다 — 할인 때문에 생긴 기존 교차다.
--
-- 획득 속도: 시작 1,000 + 하루 총 상한 3,000(계약 §15-4, 2026-08-04 개정).
-- COMMON 900은 첫날 바로, UNCOMMON 1,500도 첫날 안에 닿는다.
--
-- ⚠️ 단, 지금 clov-api main에는 EARN_* 지급 코드가 없다(clov-api #92 대기 중).
--   구현이 들어가기 전까지 실제 획득 경로는 시작 골드 1,000뿐이다.
--
-- ⚠️ 데모에서 구매·장착을 시연하려면 가격을 낮추지 말고 지갑을 채울 것.
--   되돌리기 전에 산 계정은 user_inventory_items.paid_price 에 낮은 값이 그대로 남는다.
--   db/maintenance/grant-test-gold.sql 을 쓴다(reason 은 SIGNUP_GRANT — EARN_ 계열을
--   쓰면 하루 총 상한 합산에 잡혀 그날 정상 획득이 막힌다).
--
-- ══════════════════════════════════════════════════════════════════════
-- ★ 조용히 깨지는 값 둘 — 에러가 안 난다
-- ══════════════════════════════════════════════════════════════════════
--
-- category 는 'SKIN'이 아니라 'COSTUME'이다 — ShopService.equip() 이 COSTUME만 장착을
--   허용한다(SKIN/EVENT는 ITEM_NOT_EQUIPPABLE). UI에서 '스킨'으로 불러도 데이터는
--   COSTUME 이어야 실제로 장착된다.
--
-- image_url 은 기능에 물려 있다. Mascot.jsx 의 EQUIPPED_SKIN_STATE_SPRITES 가 이 경로를
--   endsWith 로 매칭해 9개 상태 스프라이트로 갈아끼운다. 오타가 나면 에러 없이 상태
--   전환만 사라지고 default 한 장짜리 옛 코스튬처럼 동작한다.
--   폴더명은 각각 magic-school-student-common / junior-developer-uncommon /
--   explorer-uncommon 이다. metadata.json 과 Mascot.jsx 에 적힌 것과 한 글자도 다르면 안 된다.
--
-- ★ 에셋을 먼저 배포하고 이 SQL을 실행한다. 순서가 반대면 상점에 이름만 뜨고 그림이 깨진다.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT로 쓴다.

SET NAMES utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES
  ('COSTUME_CROBI_MAGIC_SCHOOL', '마법학교 학생 크로비', '뾰족 모자와 마법책을 든 크로비의 학생 복장',
   'COSTUME', 'COMMON', 900, 0,
   '/shop/skins/crobi/magic-school-student-common/default.png', 210),
  ('COSTUME_KIM_CHEOLSU_JUNIOR_DEV', '신입 개발자 김철수', '사원증을 목에 건 김철수의 첫 출근 복장',
   'COSTUME', 'UNCOMMON', 1500, 0,
   '/shop/skins/kim-cheolsu/junior-developer-uncommon/default.png', 220),
  ('COSTUME_ROB_EXPLORER', '탐험가 롭', '탐험모와 배낭을 갖춘 롭의 원정 복장',
   'COSTUME', 'UNCOMMON', 1500, 0,
   '/shop/skins/rob/explorer-uncommon/default.png', 230)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- 검증 1: 세 행이 나와야 한다. price 가 900 / 1500 / 1500, status 가 전부 ACTIVE 여야 한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다(ShopItem.isPurchasable).
-- 다만 findCatalog 에는 status 조건이 없어서 목록에는 그대로 뜬다 — "보이는데 못 사는"
-- 상태가 되므로 눈으로 status 까지 확인한다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE code IN ('COSTUME_CROBI_MAGIC_SCHOOL', 'COSTUME_KIM_CHEOLSU_JUNIOR_DEV', 'COSTUME_ROB_EXPLORER')
ORDER BY sort_order;

-- 검증 2: COMMON · UNCOMMON 두 층의 순서 확인.
-- COMMON   500 · 600 · 900 · 900 · 900
-- UNCOMMON 960 · 1,500 · 1,500 · 1,500 · 1,500
-- 같은 값이 여러 줄인 것이 정상이다(위 주석 참고).
SELECT rarity, category, code, price, discount_rate,
       ROUND(price * (100 - discount_rate) / 100) AS effective_price
FROM shop_items
WHERE rarity IN ('COMMON', 'UNCOMMON')
ORDER BY FIELD(rarity, 'COMMON', 'UNCOMMON'), effective_price;

-- 검증 3: 마스코트 스킨 전수 점검. 이제 11종이다.
-- category 가 전부 COSTUME 이고 image_url 이 전부 /shop/skins/ 로 시작해야 한다.
-- 새 것만 보지 말고 기존 것까지 같이 본다 — 스킨이 늘수록 값이 커지는 쿼리다.
SELECT code, rarity, price, category, status,
       IF(category = 'COSTUME' AND image_url LIKE '/shop/skins/%', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE image_url LIKE '/shop/skins/%'
ORDER BY sort_order;

-- 검증 4: 카탈로그 총액. 계약 §15-1 이 이 값을 인용하므로 스킨을 늘릴 때마다 다시 센다.
-- 이 배치 뒤 기대값은 75,560 (직전 71,660 + 900 + 1,500 + 1,500).
-- 하루 총 상한 3,000 기준 완주 일수는 (75,560 - 1,000) / 3,000 ≈ 25일이다.
SELECT SUM(ROUND(price * (100 - discount_rate) / 100)) AS catalog_total
FROM shop_items;
