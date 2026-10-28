ALTER TABLE activation_requests ADD COLUMN consumer_id VARCHAR(255);

UPDATE activation_requests SET consumer_id = 'local-demo';

ALTER TABLE activation_requests ALTER COLUMN consumer_id SET NOT NULL;

CREATE INDEX ix_activation_consumer_created ON activation_requests (consumer_id, created_at, id);

CREATE INDEX ix_activation_consumer_status_created
    ON activation_requests (consumer_id, status, created_at, id);

CREATE INDEX ix_activation_consumer_customer_created
    ON activation_requests (consumer_id, customer_id, created_at, id);
