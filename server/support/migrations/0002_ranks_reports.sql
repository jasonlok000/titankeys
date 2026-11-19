-- Reports of board entries, for the owner to read (no automatic hiding: owner, no filter), and short-lived rate counters for
-- the report and rank-preview endpoints. Counters hold a salted hash, never an address or an account key.
CREATE TABLE support_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    public_id TEXT NOT NULL,
    reason TEXT,
    created INTEGER NOT NULL
) STRICT;

CREATE INDEX support_reports_by_entry ON support_reports(public_id);

CREATE TABLE rate_limits (
    key TEXT NOT NULL,
    window INTEGER NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (key, window)
) STRICT;
