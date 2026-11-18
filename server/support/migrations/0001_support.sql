CREATE TABLE supporters (
    account_id TEXT PRIMARY KEY,
    public_id TEXT NOT NULL UNIQUE,
    name TEXT,
    message TEXT
) STRICT;

CREATE TABLE support_orders (
    order_hash TEXT NOT NULL,
    product_id TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    account_id TEXT NOT NULL REFERENCES supporters(account_id) ON DELETE CASCADE,
    nominal_cents INTEGER NOT NULL CHECK (nominal_cents > 0),
    cents INTEGER NOT NULL CHECK (cents >= 0 AND cents <= nominal_cents),
    PRIMARY KEY (order_hash, product_id)
) STRICT;

CREATE INDEX support_orders_by_account ON support_orders(account_id);
