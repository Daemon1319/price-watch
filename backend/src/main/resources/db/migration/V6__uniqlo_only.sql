-- Drop H&M: remove any HM product data first so the tighter CHECK can apply.
-- (Superseded for extensibility by V7, which drops the site CHECK entirely.)

DELETE FROM outbox_events
WHERE product_id IN (SELECT id FROM products WHERE site = 'HM');

DELETE FROM price_history
WHERE product_id IN (SELECT id FROM products WHERE site = 'HM');

DELETE FROM tracked_items
WHERE product_id IN (SELECT id FROM products WHERE site = 'HM');

DELETE FROM products WHERE site = 'HM';

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_site_check;

ALTER TABLE products
  ADD CONSTRAINT products_site_check CHECK (site IN ('UNIQLO'));
