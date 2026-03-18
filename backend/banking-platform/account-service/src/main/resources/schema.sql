CREATE TABLE IF NOT EXISTS account_read_model (
    account_id   VARCHAR(50) PRIMARY KEY,
    customer_id  VARCHAR(50) NOT NULL,
    type         VARCHAR(20) NOT NULL,
    currency     CHAR(3) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    opened_at    TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_account_customer ON account_read_model(customer_id);

CREATE TABLE IF NOT EXISTS outbox_messages (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB NOT NULL,
    status       VARCHAR(20) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count  INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_messages(status, occurred_at) WHERE status = 'PENDING';
