-- 코스튬(COSTUME) 아이템 6종에 임시 플레이스홀더 이미지를 연결한다.
-- 실제 파일은 clov-web/public/costumes/*.svg — 프론트 정적 자원이라 절대경로(/costumes/..)로 어디서든 로드된다.
-- 나중에 실제 아트 에셋으로 교체될 때까지의 임시 값.
UPDATE shop_items SET image_url = '/costumes/neon-hood.svg'    WHERE code = 'COSTUME_NEON_HOOD';
UPDATE shop_items SET image_url = '/costumes/starlight.svg'    WHERE code = 'COSTUME_STARLIGHT';
UPDATE shop_items SET image_url = '/costumes/cherry-set.svg'   WHERE code = 'COSTUME_CHERRY_SET';
UPDATE shop_items SET image_url = '/costumes/clover-badge.svg' WHERE code = 'COSTUME_CLOVER_BADGE';
UPDATE shop_items SET image_url = '/costumes/crobi-party.svg'  WHERE code = 'COSTUME_CROBI_PARTY';
UPDATE shop_items SET image_url = '/costumes/rob-explorer.svg' WHERE code = 'COSTUME_ROB_EXPLORER';
