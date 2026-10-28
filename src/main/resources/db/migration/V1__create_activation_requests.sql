CREATE TABLE activation_requests (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_activation_external_id UNIQUE (external_id),
    CONSTRAINT ck_activation_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_activation_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_activation_lease CHECK (
        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_activation_schedule CHECK (
        (status = 'PENDING' AND next_attempt_at IS NOT NULL)
        OR (status <> 'PENDING' AND next_attempt_at IS NULL)
    )
);

CREATE INDEX ix_activation_pending ON activation_requests (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_activation_expired_leases ON activation_requests (lease_until)
    WHERE status = 'PROCESSING';
