-- 상점 아이템 12종 전체에 아트(SVG)를 연결한다.
--
-- 배경: 코스튬 6종만 /costumes/*.svg 로 먼저 붙였고, 스킨·이벤트 6종은 image_url이 NULL이라
-- 화면에서 "스킨 아트" 같은 텍스트 플레이스홀더로 떨어졌다(이슈 #164). 나머지 6종 아트를 추가하면서
-- 경로 규칙도 /shop/{category}-{name}.svg 로 통일한다.
--
-- 실제 파일: clov-web/public/shop/*.svg (프론트 정적 자원 — 절대경로라 어느 화면에서든 로드된다)
UPDATE shop_items SET image_url = '/shop/costume-neon-hood.svg'    WHERE code = 'COSTUME_NEON_HOOD';
UPDATE shop_items SET image_url = '/shop/costume-starlight.svg'    WHERE code = 'COSTUME_STARLIGHT';
UPDATE shop_items SET image_url = '/shop/costume-cherry-set.svg'   WHERE code = 'COSTUME_CHERRY_SET';
UPDATE shop_items SET image_url = '/shop/costume-clover-badge.svg' WHERE code = 'COSTUME_CLOVER_BADGE';
UPDATE shop_items SET image_url = '/shop/costume-crobi-party.svg'  WHERE code = 'COSTUME_CROBI_PARTY';
UPDATE shop_items SET image_url = '/shop/costume-rob-explorer.svg' WHERE code = 'COSTUME_ROB_EXPLORER';

UPDATE shop_items SET image_url = '/shop/skin-golden-frame.svg'    WHERE code = 'SKIN_GOLDEN_FRAME';
UPDATE shop_items SET image_url = '/shop/skin-midnight.svg'        WHERE code = 'SKIN_MIDNIGHT';
UPDATE shop_items SET image_url = '/shop/skin-ivory-basic.svg'     WHERE code = 'SKIN_IVORY_BASIC';
UPDATE shop_items SET image_url = '/shop/skin-forest.svg'          WHERE code = 'SKIN_FOREST';

UPDATE shop_items SET image_url = '/shop/event-summer-night.svg'   WHERE code = 'EVENT_SUMMER_NIGHT';
UPDATE shop_items SET image_url = '/shop/event-first-snow.svg'     WHERE code = 'EVENT_FIRST_SNOW';
