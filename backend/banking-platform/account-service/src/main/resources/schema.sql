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

CREATE TABLE IF NOT EXISTS transfer_read_model (
    transfer_id             VARCHAR(50) PRIMARY KEY,
    source_account_id       VARCHAR(50) NOT NULL,
    destination_account_id  VARCHAR(50) NOT NULL,
    beneficiary_name        VARCHAR(120) NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                CHAR(3) NOT NULL,
    reference               VARCHAR(255),
    status                  VARCHAR(30) NOT NULL,
    ledger_reference        VARCHAR(80),
    failure_reason          VARCHAR(50),
    failure_detail          TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_entry (
    id               UUID PRIMARY KEY,
    transfer_id      VARCHAR(50) NOT NULL,
    account_id       VARCHAR(50) NOT NULL,
    entry_type       VARCHAR(10) NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    currency         CHAR(3) NOT NULL,
    booking_time     TIMESTAMPTZ NOT NULL,
    ledger_reference VARCHAR(80) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ledger_account_time ON ledger_entry(account_id, booking_time);
CREATE INDEX IF NOT EXISTS idx_ledger_transfer ON ledger_entry(transfer_id);

CREATE TABLE IF NOT EXISTS balance_projection (
    account_id         VARCHAR(50) PRIMARY KEY,
    available_balance  NUMERIC(19, 4) NOT NULL,
    currency           CHAR(3) NOT NULL,
    version            BIGINT NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    idempotency_key   VARCHAR(120) PRIMARY KEY,
    request_hash      VARCHAR(128) NOT NULL,
    method            VARCHAR(10) NOT NULL,
    path              VARCHAR(255) NOT NULL,
    status_code       INTEGER,
    response_body     TEXT,
    completed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
