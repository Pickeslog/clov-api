-- ============================================================================
-- A안: 초대 코드 "방당 1행(회전)" 마이그레이션 — 2026-07-23
-- ----------------------------------------------------------------------------
-- 이 프로젝트는 Flyway/Liquibase가 없다. 프로덕션 DB에 리더가 1회 수동 실행한다.
--
-- 목적: room_invites가 재발급마다 INSERT되어 방당 여러 행(USED 등)이 누적되던 것을
--       방당 1행으로 정리하고, UNIQUE(room_id)로 "재발급 = 제자리 회전"을 강제한다.
--
-- 애플리케이션 코드(feat/invite-code-per-room)는 이 제약을 전제로 upsert한다.
-- ⚠️ 반드시 이 마이그레이션을 먼저 적용한 뒤 새 코드를 배포/재기동할 것.
-- ⚠️ 실행 전 백업 권장. 스테이징에서 먼저 검증 권장.
-- ============================================================================

-- (선택) 사전 점검 — 현재 방당 초대 행 수(1 초과면 정리 대상):
--   SELECT room_id, COUNT(*) AS n FROM room_invites GROUP BY room_id HAVING n > 1;

START TRANSACTION;

-- 0) 방별로 남길 "대표 행" 선정: ACTIVE 우선, 그다음 최신 id.
--    (CREATE TEMPORARY TABLE은 암묵 커밋을 유발하지 않는다.)
CREATE TEMPORARY TABLE _keep_invites AS
SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY room_id
               ORDER BY (status = 'ACTIVE') DESC, id DESC
           ) AS rn
    FROM room_invites
) ranked
WHERE rn = 1;

-- 1) 삭제될 초대 행을 참조하는 가입신청의 invite_id를 NULL로(FK RESTRICT 보호; 이력 링크만 상실).
--    invite_id는 원래 nullable("코드 직접 입력 신청이면 NULL")이라 의미상 안전하다.
UPDATE room_join_requests
SET invite_id = NULL
WHERE invite_id IS NOT NULL
  AND invite_id NOT IN (SELECT id FROM _keep_invites);

-- 2) 대표 외 중복 초대 행 삭제.
DELETE FROM room_invites
WHERE id NOT IN (SELECT id FROM _keep_invites);

-- 3) 남은 행의 상태를 새 도메인(ACTIVE/CANCELED)으로 정규화(USED/EXPIRED → CANCELED).
UPDATE room_invites
SET status = 'CANCELED'
WHERE status NOT IN ('ACTIVE', 'CANCELED');

COMMIT;

DROP TEMPORARY TABLE IF EXISTS _keep_invites;

-- 4) 방당 유니크 제약 추가 + 기존 비유니크 room 인덱스 제거.
--    (ALTER는 암묵 커밋되는 DDL이라 위 DML 트랜잭션과 분리해 실행한다. 위에서 방당 1행이 보장된 뒤라 성공한다.)
ALTER TABLE room_invites
    DROP INDEX idx_room_invites_room,
    ADD UNIQUE KEY uk_room_invites_room (room_id);

-- (검증) 아래 두 쿼리가 모두 0행이어야 성공:
--   SELECT room_id, COUNT(*) AS n FROM room_invites GROUP BY room_id HAVING n > 1;   -- 방당 중복 없음
--   SHOW INDEX FROM room_invites WHERE Key_name = 'uk_room_invites_room';            -- 유니크 인덱스 존재
