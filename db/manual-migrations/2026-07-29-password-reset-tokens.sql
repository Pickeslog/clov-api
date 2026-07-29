-- ============================================================================
-- 비밀번호 재설정 토큰 테이블 신설 — 2026-07-29 (계약 §4-4, 이슈 #84)
-- ----------------------------------------------------------------------------
-- 이 프로젝트는 Flyway/Liquibase가 없다. 공유 개발 DB(st4_clov)에 리더가 1회 수동 실행한다.
--
-- 목적: 이메일 가입 사용자의 비밀번호 복구 경로를 만든다.
--       원문이 아닌 해시를 저장하고, 수명 1시간·1회용이며,
--       같은 계정이 재요청하면 이전 토큰을 폐기해 살아 있는 링크를 항상 최대 1개로 유지한다.
--
-- 유효 판정: used_at IS NULL AND revoked_at IS NULL AND expires_at > NOW()
--
-- ⚠️ 새 코드를 배포/재기동하기 전에 이 마이그레이션을 먼저 적용할 것.
--    테이블이 없으면 /auth/password/forgot 호출이 즉시 실패한다.
-- ⚠️ 기존 데이터를 건드리지 않는 순수 추가라 되돌리기는 DROP TABLE 한 줄이다.
-- ============================================================================

-- 사전 점검 — 이미 적용됐는지(1이면 적용 완료):
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = DATABASE() AND table_name = 'password_reset_tokens';

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  token_hash  VARCHAR(255) NOT NULL COMMENT '원문 아닌 해시 저장(refresh_tokens와 동일 방식)',
  expires_at  DATETIME     NOT NULL COMMENT '발급 +1시간',
  used_at     DATETIME     NULL COMMENT '1회용 — 재설정 성공 시 기록',
  revoked_at  DATETIME     NULL COMMENT '같은 계정이 재요청하면 이전 토큰 무효화',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_token_hash (token_hash),
  KEY idx_password_reset_tokens_user (user_id),
  CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사후 확인:
--   SHOW CREATE TABLE password_reset_tokens;

-- ----------------------------------------------------------------------------
-- 되돌리기 (필요 시에만)
--   DROP TABLE password_reset_tokens;
-- ----------------------------------------------------------------------------

-- 운영 메모: 만료·사용된 행은 자동 정리되지 않는다. 쌓이면 아래로 정리한다.
--   DELETE FROM password_reset_tokens
--    WHERE expires_at < NOW() - INTERVAL 30 DAY;
