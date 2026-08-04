-- 앱 배경(바탕화면) 사계절 4종을 상점 카탈로그에 등록한다. 상점의 세 번째 카테고리다.
--
-- 배경: 배경 기능은 이미 clov-web 사용자설정 > 바탕화면에 있다(lib/appBackground.js).
-- 지금은 9종 전부를 아무나 고를 수 있다 — 소유를 안 본다. 이 SQL은 그중 새로 만든
-- 사계절 4종만 유료로 돌린다.
--
-- ★ 기존 5종(lp-wood-desk · clover-coast · neon-city · minimal-clover · botanical)과
--   기본 그라디언트는 여기에 없다. 무료로 남긴다 — 리더 확정 2026-08-04.
--   무료였던 걸 유료로 바꾸는 건 뺏는 것이고, 되돌릴 수도 없다. 반대 방향(유료 → 무료)은
--   언제든 선물이 되지만 이 방향은 아니다. shop_items 에 행이 아예 없으므로
--   "RETIRED 된 유료 상품"이 아니라 "상점 밖 기본 제공"이다. 둘을 헷갈리지 말 것.
--
-- ★ category = 'BACKGROUND' — 새 값이다. DDL 마이그레이션이 필요 없다.
--     shop_items.category 는 ENUM 이 아니라 VARCHAR(20) 이고,
--     ShopService.findCatalog 의 normalize() 는 값을 그대로 통과시킨다.
--     즉 백엔드 자바 코드도 0줄 바뀐다. 프론트에 탭만 추가하면 된다.
--
--   비어 있는 'SKIN' 탭을 재활용하지 않은 이유: 2026-08-04에 자리표시 상품을 내리면서
--   SKIN·EVENT 카테고리가 둘 다 비었다. 그래서 SKIN 을 배경으로 돌리는 게 당장은 공짜로
--   보이지만, 그러면 코드는 '스킨'이라 부르고 실물은 배경인 상태가 된다. 나중에 진짜
--   마스코트 스킨을 SKIN 으로 넣는 순간 둘이 한 탭에서 섞인다.
--   ★ 지금이 이 이름을 바로잡는 가장 싼 시점이다 — 옮길 기존 행이 하나도 없다.
--
-- ★ BACKGROUND 는 장착되지 않는다. ShopService.equip() 이 COSTUME 만 허용하므로
--   ITEM_NOT_EQUIPPABLE 로 떨어진다. 이건 버그가 아니라 의도다 — 배경은 상점에서
--   장착하는 물건이 아니라 사용자설정 > 바탕화면에서 고르는 물건이고, 선택값은
--   서버가 아니라 기기-로컬(localStorage 'clov_appBgTheme')에 저장된다.
--   상점이 하는 일은 '고를 수 있는가'(소유)까지고, '무엇을 골랐는가'는 기기가 갖는다.
--
-- ★ price = 2,800 (RARE) × 4종 — 리더 확정 2026-08-04.
--
--   넷을 차등하지 않는다. 같은 날 같은 규격(1920x1080 WebP + 512 썸네일)으로 만든 한
--   세트고, 봄이 여름보다 비쌀 근거가 없다. 없는 등급 차이를 지어내면 그게 다음 사람의
--   판단을 흐린다. 같은 값 여러 줄은 이 카탈로그의 관례이기도 하다
--   (COMMON 세 줄이 900, UNCOMMON 다섯 줄이 1,500, EPIC 두 줄이 6,000).
--
--   RARE 층은 이렇게 된다.
--     2,800  ROV 조종사 타코군   <- 판매 중 RARE 는 지금 이 한 종뿐이다
--     2,800  사계절 배경 4종     <- 이번에 추가
--   판매 중 12종이 COMMON 3 · UNCOMMON 5 · RARE 1 · EPIC 2 · LEGENDARY 1 이라
--   RARE 가 제일 얇은 칸이었다. 4종이 들어가면 가운데가 채워진다.
--
--   ⚠️ 배경 하나가 마스코트 스킨(스프라이트 9장, 방 사람들 전부에게 보임)보다 비싸다.
--     배경은 이미지 1장이고 나만 본다. 그래도 이 값으로 간다: 배경은 모든 화면을 항상
--     덮는 물건이라 화면 점유가 상점에서 제일 크고, 등급 배지가 낮으면 전체 화면
--     아트워크가 싸구려로 읽힌다. 값을 노력이 아니라 체감으로 매긴 자리다 —
--     ★ 이 근거가 안 통하는 상황이 오면(예: 배경이 20종으로 늘어 흔해지면) 값을 내린다.
--
--   속도 감각: 시작 1,000 + 하루 상한 3,000(계약 §15-4). 배경 하나는 하루면 닿고
--   세트 4종은 11,200 이라 약 나흘이다.
--
-- ★ image_url 은 /bg-thumbs/*.png 다 — 512x512 둥근 모서리(rx=108) 썸네일로,
--   상점 카드가 그대로 쓰기에 맞는 규격이다. Shop.jsx 가 item.imageUrl 을 <img src> 에
--   바로 넣는다. 마스코트 스킨과 달리 /shop/skins/ 밑이 아니므로,
--   "image_url LIKE '/shop/skins/%'" 로 전수 점검하는 기존 쿼리에는 안 잡힌다(정상).
--
-- ★ code 는 lib/appBackground.js 의 id 를 그대로 대문자로 옮긴 값이다.
--   id 는 파일 경로(/backgrounds/<id>.webp)와 localStorage 저장값이라 이쪽이 원본이다.
--   ⚠️ winter-moonlit-forest 는 이름만 '토렐로의 겨울 골목'으로 바뀌고 id 는 그대로다.
--     id 를 바꾸실 거면 이 SQL 을 실행하기 전에 바꿔야 한다 — 실행 후에는 파일명 변경이
--     아니라 DB 마이그레이션이 되고, 이미 산 사람의 user_inventory_items 까지 딸려온다.
--
-- ⚠️ 이 SQL 만 실행하면 사도 아무 일이 안 일어난다.
--   Settings.jsx 가 APP_BACKGROUNDS 를 소유 여부 없이 전부 그려서, 안 사도 고를 수 있다.
--   2026-08-04 오전에 잡은 "보이는데 못 사는"(RETIRED 가 목록에 뜨던 것)의 정확한
--   거울상이다 — "샀는데 안 사도 되는". clov-web 게이팅과 같이 배포할 것.
--
-- 여러 번 실행해도 같은 결과가 되도록 UPSERT 로 쓴다.

SET NAMES utf8mb4;

-- ── 사전점검 0: code 충돌 ─────────────────────────────────────────────────
-- ★ 반드시 INSERT 전에 돌린다. ON DUPLICATE KEY UPDATE 는 code 가 겹치면 기존 행을
--   조용히 덮어쓰고, 실행 후 검증 쿼리로는 구분이 안 된다("4행 반환"은 4건이 새로 들어간
--   경우와 1건을 덮고 3건이 들어간 경우가 똑같이 나온다).
--   2026-08-04에 COSTUME_ROB_EXPLORER 가 실제로 EPIC 5,200 시드를 덮었다.
-- 결과가 0행이어야 한다. 한 행이라도 나오면 INSERT 를 멈추고 그 code 를 먼저 확인한다.
SELECT id, code, name, category, rarity, price, status
FROM shop_items
WHERE code IN ('BACKGROUND_SPRING_RAIN_CITY', 'BACKGROUND_MIDSUMMER_COVE',
               'BACKGROUND_AUTUMN_WATERCOLOR_PATH', 'BACKGROUND_WINTER_MOONLIT_FOREST');

-- ── 등록 ────────────────────────────────────────────────────────────────
INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, image_url, sort_order)
VALUES
  ('BACKGROUND_SPRING_RAIN_CITY', '봄비 뒤 벚꽃 운하', '비 갠 운하에 벚꽃이 내려앉은 봄 도시',
   'BACKGROUND', 'RARE', 2800, 0, '/bg-thumbs/spring-rain-city.png', 300),
  ('BACKGROUND_MIDSUMMER_COVE', '한여름 비밀 만', '절벽에 둘러싸인 여름 바다의 숨은 만',
   'BACKGROUND', 'RARE', 2800, 0, '/bg-thumbs/midsummer-cove.png', 310),
  ('BACKGROUND_AUTUMN_WATERCOLOR_PATH', '단풍빛 돌길', '단풍이 물든 수채화풍 돌길',
   'BACKGROUND', 'RARE', 2800, 0, '/bg-thumbs/autumn-watercolor-path.png', 320),
  ('BACKGROUND_WINTER_MOONLIT_FOREST', '토렐로의 겨울 골목', '눈 덮인 돌바닥이 아치까지 이어지는 겨울 골목',
   'BACKGROUND', 'RARE', 2800, 0, '/bg-thumbs/winter-moonlit-forest.png', 330)
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  rarity        = VALUES(rarity),
  price         = VALUES(price),
  discount_rate = VALUES(discount_rate),
  image_url     = VALUES(image_url),
  sort_order    = VALUES(sort_order);

-- ── 검증 1: 네 행이 나와야 한다 ──────────────────────────────────────────
-- price 2800, status ACTIVE, category BACKGROUND 를 눈으로 확인한다.
-- status 가 ACTIVE 가 아니면 구매가 ITEM_NOT_PURCHASABLE 로 막힌다.
SELECT id, code, name, category, rarity, price, discount_rate, status, image_url, sort_order
FROM shop_items
WHERE category = 'BACKGROUND'
ORDER BY sort_order;

-- ── 검증 2: 새 카테고리가 혼자 서는지 ────────────────────────────────────
-- BACKGROUND 4행이 나오고, SKIN·EVENT 는 ACTIVE 가 0이어야 한다(자리표시를 전부 내렸다).
-- SKIN 이나 EVENT 에 ACTIVE 가 남아 있으면 탭이 셋이 아니라 넷으로 보인다.
SELECT category,
       COUNT(*)                                             AS total,
       SUM(status = 'ACTIVE')                               AS active,
       SUM(CASE WHEN status = 'ACTIVE'
                THEN ROUND(price * (100 - discount_rate) / 100) ELSE 0 END) AS active_total
FROM shop_items
GROUP BY category
ORDER BY category;

-- ── 검증 3: 판매 중 총액 — 계약 §15-4의 "며칠이면 완주" 근거값 ───────────
-- ★ 계약서에 적힌 총액과 일수는 이 쿼리 결과로만 고친다. 계산해서 적지 말 것.
--   2026-08-04에 계산값(71,660)을 근거로 하루 상한을 정했다가, 실측이 34,800으로
--   달라서 일수가 두 배 틀렸다.
-- 일수 = (active_catalog_total - 1000) / 3000  (시작 골드 1,000 · 하루 상한 3,000)
SELECT COUNT(*)                                        AS active_items,
       SUM(ROUND(price * (100 - discount_rate) / 100)) AS active_catalog_total,
       CEIL((SUM(ROUND(price * (100 - discount_rate) / 100)) - 1000) / 3000) AS days_to_clear
FROM shop_items
WHERE status = 'ACTIVE';
