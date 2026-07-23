-- Clov 테스트용 스키마 (Testcontainers MySQL init script)
-- ⚠️ SSOT에서 파생: web-design-repository/api-spec/05-db-unified-final.md §2 DDL.
--    프로덕션 DB는 별도 관리(이 파일은 통합테스트 컨테이너 초기화 전용).
--    DDL 변경은 SSOT를 먼저 고치고 이 파일을 다시 뽑는다(수동 복제 금지).
SET NAMES utf8mb4;

-- 1. USERS
CREATE TABLE users (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  email                 VARCHAR(255) NOT NULL,
  password              VARCHAR(255) NULL COMMENT '소셜 전용 계정은 NULL',
  oauth_provider        VARCHAR(20)  NULL COMMENT '✚ kakao/naver/google',
  oauth_subject         VARCHAR(255) NULL COMMENT '✚ 소셜 고유 식별자',
  nickname              VARCHAR(50)  NOT NULL,
  profile_image_url     VARCHAR(512) NULL,
  birthdate             DATE         NULL,
  terms_agreed_at       DATETIME     NULL COMMENT '✚ 서비스 이용약관 동의 시각(이메일 가입 필수, 앱 레벨 강제)',
  privacy_agreed_at     DATETIME     NULL COMMENT '✚ 개인정보 처리방침 동의 시각(이메일 가입 필수, 앱 레벨 강제)',
  marketing_agreed_at   DATETIME     NULL COMMENT '✚ 마케팅 수신 동의 시각(선택, NULL=미동의)',
  personal_invite_code  VARCHAR(20)  NOT NULL,
  is_anonymized         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '탈퇴=익명화(기록 보존)',
  anonymized_at         DATETIME     NULL,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_invite_code (personal_invite_code),
  UNIQUE KEY uk_users_oauth (oauth_provider, oauth_subject)  -- ✚ 소셜 계정 중복 방지
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. USER_PREFERENCES ✨ (사용자설정 08 테마 pane)
CREATE TABLE user_preferences (
  user_id               BIGINT       NOT NULL,
  dark_mode             BOOLEAN      NOT NULL DEFAULT FALSE,
  custom_color          VARCHAR(20)  NULL COMMENT '물감 커스텀 색상',
  wallpaper_icon        VARCHAR(50)  NULL,
  dashboard_background  VARCHAR(50)  NULL COMMENT 'V5 벽지',
  letter_theme          VARCHAR(20)  NULL COMMENT '선물상자/우체통',
  memory_card_theme     VARCHAR(20)  NULL COMMENT '빨랫줄/겹침/일기장',
  mascot_type           VARCHAR(20)  NOT NULL DEFAULT 'crobi' COMMENT 'crobi/rob',
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_prefs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. FRIENDSHIP_ROOMS
CREATE TABLE friendship_rooms (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  name                VARCHAR(100) NOT NULL,
  description         VARCHAR(60)  NULL COMMENT '소개글 <=60자',
  theme_color         VARCHAR(20)  NULL,
  transport_type      VARCHAR(20)  NULL COMMENT '비행기/버스/배/기차',
  cover_photo_url     VARCHAR(512) NULL,
  cover_title         VARCHAR(100) NULL,
  friendship_level    INT          NOT NULL DEFAULT 1,
  exp_point           INT          NOT NULL DEFAULT 0,
  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/ARCHIVED',
  scheduled_delete_at DATETIME     NULL COMMENT '잠자는 방: INACTIVE+30일 후 삭제 예정',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. ROOM_MEMBERS
CREATE TABLE room_members (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  room_id         BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LEFT',
  is_favorite     BOOLEAN      NOT NULL DEFAULT FALSE,
  status_message  VARCHAR(100) NULL COMMENT '방별 상태메시지(내 것만 편집)',
  joined_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at         DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_members (room_id, user_id),  -- 중복 가입 방지
  KEY idx_room_members_room_status (room_id, status),  -- 정원 카운트/멤버 목록
  KEY idx_room_members_user (user_id),
  CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. ROOM_INVITES
CREATE TABLE room_invites (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  room_id      BIGINT      NOT NULL,
  created_by   BIGINT      NOT NULL COMMENT '이력용, 권한 아님',
  invite_code  VARCHAR(20) NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELED (A안: 방당 1행·다회용 회전 코드)',
  expires_at   DATETIME    NULL,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at      DATETIME    NULL COMMENT 'A안 이후 미사용(다회용) — 하위호환 위해 컬럼 보존',
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_invites_code (invite_code),
  UNIQUE KEY uk_room_invites_room (room_id),  -- A안: 방당 초대 코드 1행 강제(재발급=제자리 회전)
  CONSTRAINT fk_room_invites_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_room_invites_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. ROOM_JOIN_REQUESTS ✨ (가입 신청·승인·5분 되돌리기 — D1)
CREATE TABLE room_join_requests (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  room_id           BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL COMMENT '신청자',
  invite_id         BIGINT      NULL COMMENT '코드 직접 입력 신청이면 NULL',
  status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED/EXPIRED',
  accepted_by       BIGINT      NULL COMMENT '수락한 멤버(누구나 1명)',
  requested_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accepted_at       DATETIME    NULL,
  undo_deadline_at  DATETIME    NULL COMMENT '수락 시각+5분',
  rejected_at       DATETIME    NULL,
  version           INT         NOT NULL DEFAULT 0 COMMENT '낙관적 락(동시 수락 경합)',
  PRIMARY KEY (id),
  KEY idx_join_requests_room_status (room_id, status),
  KEY idx_join_requests_user_status (user_id, status),
  CONSTRAINT fk_join_requests_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_join_requests_applicant FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_join_requests_acceptor FOREIGN KEY (accepted_by) REFERENCES users(id),
  CONSTRAINT fk_join_requests_invite FOREIGN KEY (invite_id) REFERENCES room_invites(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. PLANS  (죽은 컬럼 plan_time/place_name/address 제거 — 화면에 입력 UI 없음)
CREATE TABLE plans (
  id                           BIGINT       NOT NULL AUTO_INCREMENT,
  room_id                      BIGINT       NOT NULL,
  writer_id                    BIGINT       NOT NULL,
  title                        VARCHAR(100) NOT NULL,
  plan_date                    DATE         NULL,
  description                  TEXT         NULL COMMENT '메모(장소/시간은 자유서식으로)',
  status                       VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/COMPLETED/CANCELED',
  memory_status                VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/CANDIDATE/WRITTEN/SKIPPED',
  completed_at                 DATETIME     NULL,
  memory_candidate_created_at  DATETIME     NULL,
  created_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plans_room_status (room_id, status),
  KEY idx_plans_room_date (room_id, plan_date),
  CONSTRAINT fk_plans_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_plans_writer FOREIGN KEY (writer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. PLAN_CHECKLISTS
CREATE TABLE plan_checklists (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  plan_id     BIGINT       NOT NULL,
  content     VARCHAR(255) NOT NULL,
  checked     BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plan_checklists_plan (plan_id),
  CONSTRAINT fk_plan_checklists_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. PLAN_STAGE_PHOTOS ✨ (인생4컷 인증사진, stage 명명 enum은 ✚ 이식)
CREATE TABLE plan_stage_photos (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  plan_id      BIGINT       NOT NULL,
  stage        VARCHAR(20)  NOT NULL COMMENT '✚ PROPOSAL/SCHEDULING/CONFIRMED/MEETING',
  image_url    VARCHAR(512) NOT NULL,
  uploaded_by  BIGINT       NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_stage (plan_id, stage) COMMENT '단계당 1장, 업로드 후 잠금(변경 불가=증거)',
  CONSTRAINT fk_plan_stage_plan FOREIGN KEY (plan_id) REFERENCES plans(id),
  CONSTRAINT fk_plan_stage_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. MEMORIES  (mood_tag 폐기 → MEMORY_TAGS 정규화, deleted_at은 ✚ 이식)
CREATE TABLE memories (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  room_id      BIGINT       NOT NULL,
  plan_id      BIGINT       NULL COMMENT 'NULL=FREE MEMORY(D3)',
  writer_id    BIGINT       NOT NULL,
  title        VARCHAR(100) NOT NULL,
  content      TEXT         NULL,
  memory_date  DATE         NULL,
  deleted_at   DATETIME     NULL COMMENT '✚ soft delete',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_memories_plan_writer (plan_id, writer_id) COMMENT '1인 1기록. plan_id NULL(FREE)은 MySQL이 중복 허용',
  KEY idx_memories_room_date (room_id, memory_date),
  KEY idx_memories_writer (writer_id),
  CONSTRAINT fk_memories_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_memories_plan FOREIGN KEY (plan_id) REFERENCES plans(id),
  CONSTRAINT fk_memories_writer FOREIGN KEY (writer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. MEMORY_IMAGES
CREATE TABLE memory_images (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  memory_id   BIGINT       NOT NULL,
  image_url   VARCHAR(512) NOT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_memory_images_memory (memory_id, sort_order),
  CONSTRAINT fk_memory_images_memory FOREIGN KEY (memory_id) REFERENCES memories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. MEMORY_TAGS ✨ (다중 해시태그 정규화 — 검색용)
CREATE TABLE memory_tags (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  memory_id  BIGINT      NOT NULL,
  tag        VARCHAR(50) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_memory_tags (memory_id, tag),  -- 같은 추억에 같은 태그 중복 차단
  KEY idx_memory_tags_tag (tag),        -- 태그로 검색
  CONSTRAINT fk_memory_tags_memory FOREIGN KEY (memory_id) REFERENCES memories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. MEMORY_PARTICIPANTS ✨ (함께한 친구 태그, 복합 PK)
CREATE TABLE memory_participants (
  memory_id  BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  PRIMARY KEY (memory_id, user_id),  -- 복합 PK로 중복 참여 원천 차단
  KEY idx_memory_participants_user (user_id),
  CONSTRAINT fk_memory_participants_memory FOREIGN KEY (memory_id) REFERENCES memories(id),
  CONSTRAINT fk_memory_participants_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. MEMORY_COMMENTS ✨ (추억 댓글 = 친구 한 줄 메시지 원천 — D2)
CREATE TABLE memory_comments (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  memory_id   BIGINT       NOT NULL,
  writer_id   BIGINT       NOT NULL,
  content     VARCHAR(255) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_memory_comments_memory (memory_id),
  CONSTRAINT fk_memory_comments_memory FOREIGN KEY (memory_id) REFERENCES memories(id),
  CONSTRAINT fk_memory_comments_writer FOREIGN KEY (writer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. LUCKY_LETTERS  (전체발송은 receiver_id 팬아웃 = 항상 단일값, 받은편지함 쿼리 분기 없음)
CREATE TABLE lucky_letters (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  room_id      BIGINT      NOT NULL,
  sender_id    BIGINT      NOT NULL,
  receiver_id  BIGINT      NOT NULL,
  content      TEXT        NOT NULL,
  emoji        VARCHAR(20) NULL COMMENT '미입력 시 프론트 기본값 💌',
  read_at      DATETIME    NULL,
  sent_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_letters_received (room_id, receiver_id, sent_at),
  KEY idx_letters_sent (room_id, sender_id, sent_at),
  CONSTRAINT fk_letters_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_letters_sender FOREIGN KEY (sender_id) REFERENCES users(id),
  CONSTRAINT fk_letters_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15-1. LETTER_FAVORITES ✨ (즐겨찾기는 보는 사람마다 다르다 — 발신자/수신자 각각)
-- lucky_letters.is_favorite(단일 컬럼)에서 분리. 한 칸이면 발신자와 수신자가 서로를 덮어썼다.
CREATE TABLE letter_favorites (
  letter_id   BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (letter_id, user_id),  -- 복합 PK로 중복 즐겨찾기 원천 차단
  KEY idx_letter_favorites_user (user_id),  -- 즐겨찾기 필터(내가 별 단 편지)
  CONSTRAINT fk_letter_favorites_letter FOREIGN KEY (letter_id) REFERENCES lucky_letters(id),
  CONSTRAINT fk_letter_favorites_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. NOTIFICATIONS ✨ (팬아웃 구조, recipient별 1행)
CREATE TABLE notifications (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  room_id       BIGINT      NOT NULL,
  recipient_id  BIGINT      NOT NULL,
  actor_id      BIGINT      NULL COMMENT '알림 유발자',
  type          VARCHAR(20) NOT NULL COMMENT 'NOTICE/FRIEND/JOIN',
  reference_id  BIGINT      NULL COMMENT '유발 리소스 id',
  is_read       BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_recipient (recipient_id, is_read, created_at),  -- 안읽음 배지
  CONSTRAINT fk_notifications_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
  CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. FRIENDSHIP_EXP_LOGS
CREATE TABLE friendship_exp_logs (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  room_id       BIGINT      NOT NULL,
  triggered_by  BIGINT      NOT NULL COMMENT '활동자, 권한 아님',
  action_type   VARCHAR(30) NOT NULL COMMENT 'PLAN_CREATE/PLAN_COMPLETE/MEMORY_WRITE/LETTER_SEND/MASCOT_INTERACT',
  exp_delta     INT         NOT NULL,
  reference_id  BIGINT      NULL COMMENT '유발 리소스 id(plan/memory/letter)',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_exp_logs_room (room_id),
  KEY idx_exp_logs_mascot (triggered_by, action_type, created_at),  -- 마스코트 하루 3회 카운트
  CONSTRAINT fk_exp_logs_room FOREIGN KEY (room_id) REFERENCES friendship_rooms(id),
  CONSTRAINT fk_exp_logs_user FOREIGN KEY (triggered_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. REFRESH_TOKENS ✨ (JWT refresh 세션 저장/무효화)
CREATE TABLE refresh_tokens (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  token_hash  VARCHAR(255) NOT NULL COMMENT '원문 아닌 해시 저장',
  expires_at  DATETIME     NOT NULL,
  revoked_at  DATETIME     NULL COMMENT '로그아웃/비번변경 시 무효화',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_tokens_user (user_id),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
