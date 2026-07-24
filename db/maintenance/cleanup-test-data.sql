-- ============================================================
-- Clov dev DB — 테스트 데이터 정리
-- 작성: 2026-07-24 (초대 플로우 2계정 라이브 검증 후)
--
-- ⚠️ 반드시 dev DB에서만. 운영 DB에서 실행 금지.
-- ⚠️ 스키마에 ON DELETE CASCADE가 없어 자식 → 부모 순서로 지운다. 순서 바꾸지 말 것.
-- ⚠️ §0~§3을 같은 커넥션(같은 세션)에서 실행할 것. TEMPORARY TABLE을 쓴다.
--    (GUI 툴에서 탭을 새로 열면 세션이 바뀌어 임시 테이블이 사라진다)
--
-- 대상 정의
--   테스트 유저 = email LIKE '%@test.local'   (verify@test.local, invA~invD-0724a@test.local)
--   테스트 방   = 멤버가 전원 테스트 유저인 방 (실제 유저가 한 명이라도 있으면 제외 = 실데이터 보호)
-- ============================================================


-- ============================================================
-- §0. 사전 점검 — "@test.local 말고 다른 테스트 계정이 남아 있나?"
--      (예전 e2e-... 계정 등. 지울 거면 §1의 조건에 직접 추가할 것)
-- ============================================================
SELECT id, email, nickname, created_at
FROM users
WHERE email LIKE '%test%' OR email LIKE '%e2e%' OR email LIKE '%example.com'
ORDER BY id;


-- ============================================================
-- §1. 대상 확정 (임시 테이블) + 미리보기
--      ★ 여기 결과를 눈으로 확인한 다음에 §2로 넘어갈 것
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_test_users;
CREATE TEMPORARY TABLE tmp_test_users (id BIGINT PRIMARY KEY);
INSERT INTO tmp_test_users (id)
SELECT id FROM users
WHERE email LIKE '%@test.local';
-- 다른 테스트 계정도 지우려면 위 조건에 OR 를 추가하거나, 아래처럼 직접 넣는다.
-- INSERT INTO tmp_test_users (id) VALUES (10);

DROP TEMPORARY TABLE IF EXISTS tmp_test_rooms;
CREATE TEMPORARY TABLE tmp_test_rooms (id BIGINT PRIMARY KEY);
INSERT INTO tmp_test_rooms (id)
SELECT rm.room_id
FROM room_members rm
GROUP BY rm.room_id
HAVING SUM(CASE WHEN rm.user_id IN (SELECT id FROM tmp_test_users) THEN 0 ELSE 1 END) = 0;

-- 미리보기 1: 지워질 유저
SELECT u.id, u.email, u.nickname FROM users u
JOIN tmp_test_users t ON t.id = u.id
ORDER BY u.id;

-- 미리보기 2: 지워질 방 (멤버 전원이 테스트 계정인 방만)
SELECT r.id, r.name, r.created_at,
       (SELECT COUNT(*) FROM room_members m WHERE m.room_id = r.id) AS member_rows
FROM friendship_rooms r
JOIN tmp_test_rooms t ON t.id = r.id
ORDER BY r.id;

-- 미리보기 3: 테스트 유저가 "실제 방"에 남긴 흔적이 있는지 (있으면 그 방 데이터도 함께 지워진다)
SELECT '실제 방의 추억' AS kind, COUNT(*) AS cnt FROM memories
 WHERE writer_id IN (SELECT id FROM tmp_test_users) AND room_id NOT IN (SELECT id FROM tmp_test_rooms)
UNION ALL
SELECT '실제 방의 편지', COUNT(*) FROM lucky_letters
 WHERE (sender_id IN (SELECT id FROM tmp_test_users) OR receiver_id IN (SELECT id FROM tmp_test_users))
   AND room_id NOT IN (SELECT id FROM tmp_test_rooms)
UNION ALL
SELECT '실제 방의 멤버십', COUNT(*) FROM room_members
 WHERE user_id IN (SELECT id FROM tmp_test_users) AND room_id NOT IN (SELECT id FROM tmp_test_rooms);
-- ↑ 전부 0 이면 실데이터와 완전히 분리된 상태. 0이 아니면 §2가 그 행들도 지운다(유저 삭제 전제).


-- ============================================================
-- §2. 삭제 (자식 → 부모)
--      미리보기 확인 후 아래 블록 전체를 한 번에 실행
-- ============================================================
START TRANSACTION;

-- 2-1. 편지
DELETE FROM letter_favorites
 WHERE user_id IN (SELECT id FROM tmp_test_users)
    OR letter_id IN (SELECT id FROM lucky_letters
                      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                         OR sender_id IN (SELECT id FROM tmp_test_users)
                         OR receiver_id IN (SELECT id FROM tmp_test_users));

DELETE FROM lucky_letters
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR sender_id IN (SELECT id FROM tmp_test_users)
    OR receiver_id IN (SELECT id FROM tmp_test_users);

-- 2-2. 추억 (자식 4종 → 본체)
DELETE FROM memory_comments
 WHERE writer_id IN (SELECT id FROM tmp_test_users)
    OR memory_id IN (SELECT id FROM memories
                      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                         OR writer_id IN (SELECT id FROM tmp_test_users));

DELETE FROM memory_participants
 WHERE user_id IN (SELECT id FROM tmp_test_users)
    OR memory_id IN (SELECT id FROM memories
                      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                         OR writer_id IN (SELECT id FROM tmp_test_users));

DELETE FROM memory_tags
 WHERE memory_id IN (SELECT id FROM memories
                      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                         OR writer_id IN (SELECT id FROM tmp_test_users));

DELETE FROM memory_images
 WHERE memory_id IN (SELECT id FROM memories
                      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                         OR writer_id IN (SELECT id FROM tmp_test_users));

-- memories 는 plans 를 참조하므로 plans 보다 먼저 지운다
DELETE FROM memories
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR writer_id IN (SELECT id FROM tmp_test_users);

-- 2-3. 약속 (자식 2종 → 본체)
DELETE FROM plan_stage_photos
 WHERE uploaded_by IN (SELECT id FROM tmp_test_users)
    OR plan_id IN (SELECT id FROM plans
                    WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                       OR writer_id IN (SELECT id FROM tmp_test_users));

DELETE FROM plan_checklists
 WHERE plan_id IN (SELECT id FROM plans
                    WHERE room_id IN (SELECT id FROM tmp_test_rooms)
                       OR writer_id IN (SELECT id FROM tmp_test_users));

DELETE FROM plans
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR writer_id IN (SELECT id FROM tmp_test_users);

-- 2-4. 알림 · 경험치 로그
DELETE FROM notifications
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR recipient_id IN (SELECT id FROM tmp_test_users)
    OR actor_id IN (SELECT id FROM tmp_test_users);

DELETE FROM friendship_exp_logs
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR triggered_by IN (SELECT id FROM tmp_test_users);

-- 2-5. 초대 · 가입 신청
--      room_join_requests 가 room_invites(invite_id) 를 참조 → 신청을 먼저 지운다
DELETE FROM room_join_requests
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR user_id IN (SELECT id FROM tmp_test_users)
    OR accepted_by IN (SELECT id FROM tmp_test_users);

DELETE FROM room_invites
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR created_by IN (SELECT id FROM tmp_test_users);

-- 2-6. 멤버십 → 방
DELETE FROM room_members
 WHERE room_id IN (SELECT id FROM tmp_test_rooms)
    OR user_id IN (SELECT id FROM tmp_test_users);

DELETE FROM friendship_rooms
 WHERE id IN (SELECT id FROM tmp_test_rooms);

-- 2-7. 유저 부속 → 유저
DELETE FROM refresh_tokens  WHERE user_id IN (SELECT id FROM tmp_test_users);
DELETE FROM user_preferences WHERE user_id IN (SELECT id FROM tmp_test_users);
DELETE FROM users            WHERE id      IN (SELECT id FROM tmp_test_users);

COMMIT;
-- 문제가 보이면 COMMIT 대신: ROLLBACK;


-- ============================================================
-- §3. 검증 — 전부 0 이어야 한다
-- ============================================================
SELECT 'users'              AS t, COUNT(*) AS remain FROM users            WHERE email LIKE '%@test.local'
UNION ALL SELECT 'rooms',            COUNT(*) FROM friendship_rooms  WHERE id      IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'room_members',     COUNT(*) FROM room_members      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'room_invites',     COUNT(*) FROM room_invites      WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'join_requests',    COUNT(*) FROM room_join_requests WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'notifications',    COUNT(*) FROM notifications     WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'memories',         COUNT(*) FROM memories          WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'plans',            COUNT(*) FROM plans             WHERE room_id IN (SELECT id FROM tmp_test_rooms)
UNION ALL SELECT 'letters',          COUNT(*) FROM lucky_letters     WHERE room_id IN (SELECT id FROM tmp_test_rooms);

DROP TEMPORARY TABLE IF EXISTS tmp_test_rooms;
DROP TEMPORARY TABLE IF EXISTS tmp_test_users;
