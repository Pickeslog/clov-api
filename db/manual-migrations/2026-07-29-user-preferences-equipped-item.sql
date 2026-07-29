-- 마스코트 꾸미기(장착) — 보유한 COSTUME 아이템 중 하나를 마스코트에 장착.
-- 장착 상태는 사용자 설정(user_preferences)에 둔다: 마스코트 표시 자체가 이미
-- preferences.mascot_type을 보는 화면이라, 장착 아이템도 그 조회 흐름에 자연스럽게 얹힌다.
ALTER TABLE user_preferences
  ADD COLUMN equipped_item_id BIGINT NULL COMMENT '장착 중인 shop_items.id, COSTUME만 해당. NULL=미장착',
  ADD CONSTRAINT fk_user_preferences_equipped_item FOREIGN KEY (equipped_item_id) REFERENCES shop_items(id);
