CREATE TABLE ticket_email_jobs(
 booking_id VARCHAR(36) PRIMARY KEY REFERENCES bookings(id),
 recipient VARCHAR(254) NOT NULL,
 state VARCHAR(16) NOT NULL,
 attempts INT NOT NULL,
 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
 lease_until TIMESTAMP(6) WITH TIME ZONE,
 sent_at TIMESTAMP(6) WITH TIME ZONE,
 attempt_id VARCHAR(36),
 last_error VARCHAR(100)
);
CREATE INDEX ix_ticket_email_due ON ticket_email_jobs(state,next_attempt_at);
