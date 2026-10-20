-- NON_COFFEE may include drinks other than tea; preserve it as a neutral category.
UPDATE menus SET category = 'OTHER' WHERE category = 'NON_COFFEE';
-- Pending candidates need a fresh choice; historical candidates keep a valid neutral category.
UPDATE menu_image_items
SET detected_category = CASE WHEN status = 'PENDING' THEN NULL ELSE 'OTHER' END
WHERE detected_category = 'NON_COFFEE';

ALTER TABLE menus
  DROP CHECK ck_menu_category,
  ADD CONSTRAINT ck_menu_category CHECK
    (category IN ('COFFEE','TEA','BEVERAGE','CAKE','BAKERY','OTHER'));
