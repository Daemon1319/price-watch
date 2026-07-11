-- Site values are validated by the application (Site enum + Scraper beans).
-- Drop the DB CHECK so adding a storefront later does not require a migration
-- that only widens a constraint — only a new enum constant + Scraper class.

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_site_check;
