-- Reusable trigger function to auto-maintain updated_at columns.
-- Attached per-table below rather than handled in application code, so it's
-- impossible to forget on any future direct SQL update.
CREATE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  email VARCHAR(255) NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness without the citext extension: enforced on the
-- lowercased value. The application must lowercase email before every
-- insert/lookup (AuthService's job, not the DB's).
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

CREATE TRIGGER users_set_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  normalized_url TEXT NOT NULL,
  original_url TEXT NOT NULL,
  site VARCHAR(20) NOT NULL CHECK (site IN ('UNIQLO', 'HM')),
  last_known_price NUMERIC(10, 2),
  last_known_stock_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (last_known_stock_status IN ('IN_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')),
  thumbnail_url TEXT,
  last_checked_at TIMESTAMPTZ,
  consecutive_failures INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX products_normalized_url_idx ON products (normalized_url);

-- Speeds up the "products in UNHEALTHY state" observability metric (plan
-- §12): a partial index only covering the rows that query actually matches.
CREATE INDEX products_consecutive_failures_idx ON products (consecutive_failures)
  WHERE consecutive_failures >= 5;

CREATE TRIGGER products_set_updated_at
  BEFORE UPDATE ON products
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE TABLE tracked_items (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  price_threshold NUMERIC(10, 2),
  notify_on_restock_only BOOLEAN NOT NULL DEFAULT false,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT tracked_items_user_product_unique UNIQUE (user_id, product_id)
);

CREATE INDEX tracked_items_user_id_idx ON tracked_items (user_id);
CREATE INDEX tracked_items_product_id_idx ON tracked_items (product_id);

CREATE TRIGGER tracked_items_set_updated_at
  BEFORE UPDATE ON tracked_items
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();