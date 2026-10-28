ALTER TABLE ticket_batches ADD smile_sync_state VARCHAR(20);
ALTER TABLE ticket_batches ADD smile_code_mapping VARCHAR(20);
ALTER TABLE ticket_batches ADD smile_synced_at DATETIMEOFFSET;
ALTER TABLE ticket_stock ADD smile_gate_barcode VARCHAR(128);
ALTER TABLE tickets ADD smile_gate_barcode VARCHAR(128);
CREATE TABLE smile_sync_jobs(batch_id VARCHAR(36) PRIMARY KEY REFERENCES ticket_batches(id),state VARCHAR(20) NOT NULL,attempts INT NOT NULL,created_at DATETIMEOFFSET NOT NULL,next_attempt_at DATETIMEOFFSET NOT NULL,lease_until DATETIMEOFFSET,synced_at DATETIMEOFFSET,attempt_id VARCHAR(36),last_error NVARCHAR(500));
CREATE INDEX ix_smile_jobs_due ON smile_sync_jobs(state,next_attempt_at);
