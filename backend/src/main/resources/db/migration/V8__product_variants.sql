-- First-class Uniqlo color/size on each product row.
-- Identity remains normalized_url (includes colorCode + sizeCode query params);
-- these columns make variants visible in API responses and notifications.

ALTER TABLE products
  ADD COLUMN color_code VARCHAR(20),
  ADD COLUMN size_code VARCHAR(20),
  ADD COLUMN color_name VARCHAR(100),
  ADD COLUMN size_name VARCHAR(100);

CREATE INDEX products_color_size_idx ON products (color_code, size_code)
  WHERE color_code IS NOT NULL AND size_code IS NOT NULL;
