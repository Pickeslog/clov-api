-- ============================================================
-- 알림 구조 개선 — sub_type / payload 추가 (계약 §13, 이슈 #64)
--
-- ⚠️ Flyway 없음 → 기존 프로덕션 DB에 이 파일을 1회 수동 실행한다.
--    신규 DB는 src/test/resources/schema.sql이 이미 반영돼 있어 불필요.
-- ⚠️ 컬럼 추가 → 기존 행 백필 → NOT NULL 순서를 지킬 것.
--    기존 행이 있는 상태에서 바로 NOT NULL을 붙이면 실패한다.
-- ============================================================

-- 1. 컬럼 추가 (sub_type은 일단 NULL 허용으로 붙인다)
ALTER TABLE notifications
  ADD COLUMN sub_type VARCHAR(30) NULL AFTER type,
  ADD COLUMN payload  JSON        NULL AFTER reference_id;

-- 2. 기존 행 백필
--    현재 생성 지점은 두 곳뿐: 방 설정 변경(NOTICE) / 가입 신청(JOIN).
--    방 설정 변경은 이번에 FRIEND 탭으로 이동한다(친구 활동).
UPDATE notifications SET type = 'FRIEND', sub_type = 'ROOM_UPDATE' WHERE type = 'NOTICE';
UPDATE notifications SET sub_type = 'JOIN_REQUEST'                 WHERE type = 'JOIN';

-- 3. 백필 뒤 NOT NULL 확정
ALTER TABLE notifications
  MODIFY COLUMN sub_type VARCHAR(30) NOT NULL
    COMMENT '이벤트: ROOM_UPDATE/MEMORY_WRITE/LETTER_RECEIVE/PLAN_CREATE/PLAN_COMPLETE/LEVEL_UP/JOIN_REQUEST/JOIN_ACCEPTED/ADMIN_NOTICE';

-- 검증 — sub_type이 NULL인 행이 없어야 한다
SELECT type, sub_type, COUNT(*) FROM notifications GROUP BY type, sub_type;
