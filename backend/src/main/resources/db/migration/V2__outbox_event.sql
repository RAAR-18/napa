CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_by UUID,
    locked_until TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((status = 'PROCESSING') = (claimed_by IS NOT NULL AND locked_until IS NOT NULL)),
    CHECK ((status = 'PROCESSED') = (processed_at IS NOT NULL))
);

CREATE INDEX outbox_event_pending_idx
    ON outbox_event (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX outbox_event_expired_lease_idx
    ON outbox_event (locked_until)
    WHERE status = 'PROCESSING';
