CREATE TABLE outbox_events (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  event_type VARCHAR(20) NOT NULL
    CHECK (event_type IN ('PRICE_DROP', 'PRICE_INCREASE', 'RESTOCK', 'OUT_OF_STOCK')),
  payload JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The outbox relay (plan §5, step 4) polls exactly this shape of query every
-- 5-10s: WHERE published_at IS NULL ORDER BY created_at LIMIT 50. A partial
-- index — only covering unpublished rows — keeps that query fast forever,
-- since published rows (the vast majority, over time) never need to be
-- touched by this index again.
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at)
  WHERE published_at IS NULL;