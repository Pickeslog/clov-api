-- 배경 4종의 상점 카드 그림을 클로버 아이콘 썸네일에서 실제 그림으로 바꾼다.
--
-- 배경: 등록할 때 image_url 을 /bg-thumbs/*.png(사용자설정 스와치용 클로버 아이콘)로 뒀는데,
-- 상점에서는 그게 틀린 그림이다. 상점 카드는 "이걸 사면 뭐가 되나"를 보여주는 자리고,
-- 배경을 사는 사람이 보고 싶은 건 클로버가 아니라 그 배경이다.
--
-- ★ 카드 규격에 맞춰 새로 잘랐다. .shop-art 가 aspect-ratio: 1/1 + object-fit: contain 이라
--   1920x1080 원본을 그대로 넣으면 위아래에 빈 띠가 생긴다. 그래서 가운데 1080 정사각을
--   잘라 640x640 WebP 로 굽는다(마스코트 스킨 스프라이트와 같은 640 규격).
--   넷 다 소실점이 화면 가운데라 중앙 크롭이 맞는다 — 다음 배경이 한쪽으로 치우친
--   구도면 크롭 좌표를 그림마다 따로 잡을 것.
--
-- ★★ 이 UPDATE 가 안전한 이유 — 소유 대조를 code 로 하기 때문이다.
--   사용자설정의 잠금 판정(isBackgroundUnlocked)이 shop_items.code 를 본다.
--   처음에 ShopItemResponse 에 code 가 없어서 imageUrl 로 대조하려다 접었는데,
--   그 우회를 썼으면 이 UPDATE 한 번으로 이미 산 사람의 배경이 전부 잠겼다.
--   ★ 표현용 필드(image_url)는 앞으로도 이렇게 바뀐다. 신원 확인에 쓰지 말 것.
--
-- ★ /bg-thumbs/*.png 는 지우지 않는다. 사용자설정 > 바탕화면 스와치가 계속 쓴다.
--   두 그림은 쓰임이 다르다:
--     /bg-thumbs/<id>.png        512x512, 둥근 모서리, 클로버 마크 — 설정 스와치(작게 나열)
--     /shop/backgrounds/<id>.webp 640x640, 실제 그림 크롭      — 상점 카드(하나씩 크게)
--
-- ★ 에셋을 먼저 배포하고 이 SQL 을 실행한다. 순서가 반대면 카드 그림이 깨진다.

SET NAMES utf8mb4;

-- ── 사전점검: 지금 값 확인 ───────────────────────────────────────────────
-- 네 행이 /bg-thumbs/ 를 보고 있어야 한다. 이미 /shop/backgrounds/ 면 실행된 것이다.
SELECT code, name, image_url FROM shop_items
WHERE category = 'BACKGROUND' ORDER BY sort_order;

-- ── 교체 ────────────────────────────────────────────────────────────────
UPDATE shop_items
SET image_url = CONCAT('/shop/backgrounds/', LOWER(REPLACE(SUBSTRING(code, 12), '_', '-')), '.webp')
WHERE category = 'BACKGROUND';

-- ── 검증 1: 네 행이 아래와 정확히 같아야 한다 ───────────────────────────
--   BACKGROUND_SPRING_RAIN_CITY        /shop/backgrounds/spring-rain-city.webp
--   BACKGROUND_MIDSUMMER_COVE          /shop/backgrounds/midsummer-cove.webp
--   BACKGROUND_AUTUMN_WATERCOLOR_PATH  /shop/backgrounds/autumn-watercolor-path.webp
--   BACKGROUND_WINTER_MOONLIT_FOREST   /shop/backgrounds/winter-moonlit-forest.webp
-- ⚠️ 위 CONCAT 은 code 에서 'BACKGROUND_' 11글자를 떼고 소문자·하이픈으로 바꾼 것이다.
--   code 와 파일명 규칙이 어긋나는 배경이 생기면 이 식이 조용히 틀린 경로를 만든다.
--   그래서 눈으로 넷을 다 확인한다 — 카드 그림이 깨지는 건 배포 후에야 보인다.
SELECT code, name, image_url,
       IF(image_url LIKE '/shop/backgrounds/%.webp', 'OK', '★ 확인') AS chk
FROM shop_items
WHERE category = 'BACKGROUND'
ORDER BY sort_order;

-- ── 검증 2: 다른 카테고리를 안 건드렸는지 ────────────────────────────────
-- 마스코트 스킨 13종이 전부 /shop/skins/ 를 그대로 보고 있어야 한다.
SELECT COUNT(*) AS skin_rows,
       SUM(image_url LIKE '/shop/skins/%') AS still_skins
FROM shop_items
WHERE category = 'COSTUME' AND image_url LIKE '/shop/%';

-- ── 검증 3: 가격은 안 건드렸다 — 총액이 52,000 그대로여야 한다 ───────────
SELECT COUNT(*)                                        AS active_items,
       SUM(ROUND(price * (100 - discount_rate) / 100)) AS active_catalog_total
FROM shop_items
WHERE status = 'ACTIVE';
