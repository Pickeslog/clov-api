-- 상점(shop) 도메인 신설 — 프론트 PR #111(feat/96-shop-ui)이 이미 병합 대기 중이라
-- 그 계약(GET /shop/items, /shop/wallet, /shop/inventory, POST /shop/items/{id}/purchase)에
-- 맞춰 백엔드를 뒤따라 붙인다. DOMAIN-NAMING-REGISTRY / API-CONTRACT SSOT에는 아직
-- 없는 도메인이라 등록 전 임시 스키마 — 정식 반영 시 SSOT에 §번호를 받아야 한다.
SET NAMES utf8mb4;

CREATE TABLE shop_items (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  name           VARCHAR(100) NOT NULL,
  description    VARCHAR(255) NULL,
  category       VARCHAR(20)  NOT NULL COMMENT 'COSTUME/SKIN/EVENT',
  rarity         VARCHAR(20)  NOT NULL COMMENT 'COMMON/UNCOMMON/RARE/EPIC/LEGENDARY',
  price          INT          NOT NULL,
  discount_rate  INT          NOT NULL DEFAULT 0 COMMENT '0~100, 주간 할인용',
  image_url      VARCHAR(512) NULL,
  purchasable    BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '판매 종료된 한정 아이템 등은 FALSE',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 재화는 사용자 단위(방과 무관) — shop.js 주석 참고.
-- 아직 골드 획득 동선(XP 연동 등)이 SSOT에 없어 첫 조회 시 지갑을 만들며 시작 골드를 지급한다.
CREATE TABLE user_wallets (
  user_id       BIGINT   NOT NULL,
  gold_balance  BIGINT   NOT NULL DEFAULT 0,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_shop_items (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  user_id       BIGINT   NOT NULL,
  item_id       BIGINT   NOT NULL,
  purchased_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_shop_items_user_item (user_id, item_id),
  CONSTRAINT fk_user_shop_items_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_shop_items_item FOREIGN KEY (item_id) REFERENCES shop_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 시드 데이터 — 프론트 화면 확인용 최소 카탈로그.
INSERT INTO shop_items (name, description, category, rarity, price, discount_rate, image_url, purchasable) VALUES
('클로버 반다나', '기본 코스튬', 'COSTUME', 'COMMON', 100, 0, NULL, TRUE),
('숲 탐험가 모자', '숲 테마 코스튬', 'COSTUME', 'UNCOMMON', 250, 10, NULL, TRUE),
('은하수 망토', '희귀 코스튬', 'COSTUME', 'RARE', 600, 0, NULL, TRUE),
('황금 왕관', '영웅 등급 코스튬', 'COSTUME', 'EPIC', 1500, 20, NULL, TRUE),
('전설의 클로버 갑옷', '전설 등급 코스튬', 'COSTUME', 'LEGENDARY', 5000, 0, NULL, TRUE),
('파스텔 스킨', '기본 스킨', 'SKIN', 'COMMON', 80, 0, NULL, TRUE),
('네온 스킨', '고급 스킨', 'SKIN', 'UNCOMMON', 300, 0, NULL, TRUE),
('오로라 스킨', '희귀 스킨', 'SKIN', 'RARE', 700, 15, NULL, TRUE),
('여름 축제 한정 세트', '기간 한정 이벤트 아이템', 'EVENT', 'RARE', 450, 0, NULL, TRUE),
('겨울 한정 눈사람 스킨', '작년 이벤트 종료 아이템(재판매 안 함)', 'EVENT', 'EPIC', 1200, 0, NULL, FALSE);
