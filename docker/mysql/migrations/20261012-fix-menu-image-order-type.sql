-- 기존 메뉴판 마이그레이션이 적용된 DB에서만 사용한다.
-- 백업을 확인하고 image_order가 TINYINT UNSIGNED일 때 한 번 실행한다.
-- 이미 INT이면 재실행하지 않는다. 데이터와 기존 CHECK/UNIQUE는 유지한다.
ALTER TABLE menu_images
  MODIFY COLUMN image_order INT NOT NULL;
