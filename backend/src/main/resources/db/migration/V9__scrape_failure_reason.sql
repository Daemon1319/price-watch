-- Last scrape failure classification (for UI + smarter unhealthy handling).
-- Null when the product's last check succeeded or never failed.

ALTER TABLE products
  ADD COLUMN last_failure_reason VARCHAR(40),
  ADD COLUMN last_failure_detail VARCHAR(500);

ALTER TABLE products
  ADD CONSTRAINT products_last_failure_reason_check
  CHECK (
    last_failure_reason IS NULL
    OR last_failure_reason IN (
      'NETWORK',
      'HTTP_4XX',
      'HTTP_5XX',
      'VARIANT_MISSING',
      'PRODUCT_UNAVAILABLE',
      'PARSE',
      'UNKNOWN'
    )
  );
