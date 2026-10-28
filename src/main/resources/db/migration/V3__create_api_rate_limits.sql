CREATE TABLE api_rate_limits (
    consumer_id VARCHAR(255) PRIMARY KEY,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count BIGINT NOT NULL CHECK (request_count > 0)
);

CREATE INDEX ix_api_rate_limits_window ON api_rate_limits (window_started_at);
