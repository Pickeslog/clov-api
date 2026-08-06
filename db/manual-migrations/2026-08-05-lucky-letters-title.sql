-- ============================================================
-- 행운편지 제목(title) 컬럼 추가 (계약 §행운편지, 이슈 #352)
--
-- ⚠️ Flyway 없음 → 기존 프로덕션 DB에 이 파일을 1회 수동 실행한다.
--    신규 DB는 src/test/resources/schema.sql이 이미 반영돼 있어 불필요.
-- 제목은 선택 입력이라 NOT NULL 백필 단계가 필요 없다 — 기존 행은 그대로 NULL.
-- ============================================================

ALTER TABLE lucky_letters
  ADD COLUMN title VARCHAR(60) NULL COMMENT '선택 입력 — 미입력 시 NULL' AFTER receiver_id;
