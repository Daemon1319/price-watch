CREATE TABLE price_history (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  price NUMERIC(10, 2) NOT NULL,
  stock_status VARCHAR(20) NOT NULL
    CHECK (stock_status IN ('IN_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only, high-volume table: every scrape that detects a change inserts
-- here (plan §5, step 3d). uuidv7()'s time-ordered prefix keeps this index
-- insert-friendly as it grows, instead of scattering writes randomly across
-- the B-tree the way uuidv4 would.
CREATE INDEX price_history_product_id_recorded_at_idx
  ON price_history (product_id, recorded_at DESC);