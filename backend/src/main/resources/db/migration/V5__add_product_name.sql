-- Product display name/title, as scraped from the page (e.g. "Uniqlo AIRism
-- Cotton Crew Neck T-Shirt"). Nullable: a scrape can succeed at extracting
-- price/stock while failing to find a clean title, and that shouldn't block
-- the row from being created — callers should fall back to displaying the
-- URL when this is null, not treat it as an error.
ALTER TABLE products ADD COLUMN name TEXT;