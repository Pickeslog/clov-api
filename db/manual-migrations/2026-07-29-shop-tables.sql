-- 상점(shop) 도메인 — 이 스키마는 이미 개발 DB(st4_clov)에 존재한다(2026-07-25 생성 흔적,
-- 시드 아이템 12종 + 테스트 지갑/구매/원장 데이터 포함). 백엔드 코드가 뒤늦게 이 스키마를
-- 뒤따라 구현하며, 문서화/다른 환경 재현 목적으로 이 파일을 추가한다.
-- DOMAIN-NAMING-REGISTRY / API-CONTRACT SSOT에는 아직 미등록 — 정식 반영 시 SSOT 등록 필요.
SET NAMES utf8mb4;

CREATE TABLE shop_items (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(50)  NOT NULL COMMENT '내부 식별 코드, 예: COSTUME_NEON_HOOD',
  name           VARCHAR(100) NOT NULL,
  description    VARCHAR(200) NULL,
  category       VARCHAR(20)  NOT NULL COMMENT 'COSTUME/SKIN/EVENT',
  rarity         VARCHAR(20)  NOT NULL COMMENT 'COMMON/UNCOMMON/RARE/EPIC/LEGENDARY',
  price          INT          NOT NULL,
  discount_rate  INT          NOT NULL DEFAULT 0 COMMENT '0~100, 주간 할인용',
  image_url      VARCHAR(512) NULL,
  status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE만 구매 가능, RETIRED 등은 판매 종료',
  sort_order     INT          NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_items_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 재화는 사용자 단위(방과 무관). 첫 지갑 조회 시 SIGNUP_GRANT로 시작 골드를 지급하며 생성한다.
CREATE TABLE user_wallets (
  user_id     BIGINT   NOT NULL,
  balance     INT      NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_inventory_items (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  user_id       BIGINT   NOT NULL,
  item_id       BIGINT   NOT NULL,
  paid_price    INT      NOT NULL COMMENT '구매 시점의 최종가(할인 반영) — 이후 가격 변동과 무관하게 고정',
  purchased_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_inventory_items_user_item (user_id, item_id),
  CONSTRAINT fk_user_inventory_items_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_inventory_items_item FOREIGN KEY (item_id) REFERENCES shop_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 지갑 변동 원장. reason: SIGNUP_GRANT(지갑 최초 생성 지급) / PURCHASE(구매 차감).
CREATE TABLE wallet_transactions (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  user_id        BIGINT   NOT NULL,
  reason         VARCHAR(30) NOT NULL,
  amount         INT      NOT NULL COMMENT '증감분 — 지급은 양수, 차감은 음수',
  balance_after  INT      NOT NULL,
  reference_id   BIGINT   NULL COMMENT 'PURCHASE면 shop_items.id, SIGNUP_GRANT면 NULL',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wallet_transactions_user (user_id, created_at),
  CONSTRAINT fk_wallet_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shop_items (code, name, description, category, rarity, price, discount_rate, sort_order) VALUES
('COSTUME_NEON_HOOD', '네온 후드 코스튬', '어두운 밤에도 빛나는 네온 후드', 'COSTUME', 'RARE', 2400, 30, 10),
('SKIN_GOLDEN_FRAME', '골든 프레임 스킨', '추억 카드에 황금 테두리를 두른다', 'SKIN', 'EPIC', 4800, 30, 20),
('SKIN_MIDNIGHT', '미드나잇 테마 스킨', '깊은 밤 하늘빛 배경 테마', 'SKIN', 'RARE', 2000, 50, 30),
('COSTUME_STARLIGHT', '별빛 이펙트 코스튬', '마스코트 주위로 별이 흩날린다', 'COSTUME', 'UNCOMMON', 1200, 20, 40),
('COSTUME_CHERRY_SET', '벚꽃 코스튬 세트', '봄 한정 벚꽃 풀세트', 'COSTUME', 'LEGENDARY', 9800, 30, 50),
('COSTUME_CLOVER_BADGE', '클로버 배지 코스튬', '네잎클로버 배지 하나로 산뜻하게', 'COSTUME', 'COMMON', 600, 0, 60),
('SKIN_IVORY_BASIC', '아이보리 기본 스킨', '어디에나 어울리는 기본 아이보리', 'SKIN', 'COMMON', 500, 0, 70),
('SKIN_FOREST', '포레스트 테마 스킨', 'Clov 시그니처 포레스트 그린', 'SKIN', 'UNCOMMON', 1500, 0, 80),
('COSTUME_CROBI_PARTY', '크로비 파티모자', '크로비를 위한 고깔모자', 'COSTUME', 'RARE', 2200, 0, 90),
('COSTUME_ROB_EXPLORER', '롭 탐험가 코스튬', '롭이 떠나는 모험 복장', 'COSTUME', 'EPIC', 5200, 0, 100),
('EVENT_SUMMER_NIGHT', '한정 여름밤 스킨', '여름밤 불꽃놀이 한정 테마', 'EVENT', 'LEGENDARY', 12000, 0, 110),
('EVENT_FIRST_SNOW', '한정 첫눈 프레임', '첫눈 오는 날 한정 프레임', 'EVENT', 'EPIC', 6400, 0, 120);

-- 코스튬 6종 image_url 채우기 — 상세는 2026-07-29-costume-image-urls.sql 참고.
UPDATE shop_items SET image_url = '/costumes/neon-hood.svg'    WHERE code = 'COSTUME_NEON_HOOD';
UPDATE shop_items SET image_url = '/costumes/starlight.svg'    WHERE code = 'COSTUME_STARLIGHT';
UPDATE shop_items SET image_url = '/costumes/cherry-set.svg'   WHERE code = 'COSTUME_CHERRY_SET';
UPDATE shop_items SET image_url = '/costumes/clover-badge.svg' WHERE code = 'COSTUME_CLOVER_BADGE';
UPDATE shop_items SET image_url = '/costumes/crobi-party.svg'  WHERE code = 'COSTUME_CROBI_PARTY';
UPDATE shop_items SET image_url = '/costumes/rob-explorer.svg' WHERE code = 'COSTUME_ROB_EXPLORER';
