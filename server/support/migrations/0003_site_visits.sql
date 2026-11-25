-- Website visit counts (owner, 2026-11-25: "add some analytics"): one counter per day, page, source and event. No address,
-- no cookie and no identifier; the source is a site name such as "facebook" or "reddit", or "direct".
CREATE TABLE site_visits (
    day TEXT NOT NULL,
    page TEXT NOT NULL,
    source TEXT NOT NULL,
    event TEXT NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (day, page, source, event)
) STRICT;
