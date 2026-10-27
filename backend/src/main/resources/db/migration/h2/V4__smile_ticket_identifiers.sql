-- Preserve existing batches, stock and paid tickets; Smile integration itself is deferred.
ALTER TABLE ticket_batches ADD smile_ticket_type_code VARCHAR(64);
ALTER TABLE ticket_batches ADD smile_ticket_type_name VARCHAR(150);
ALTER TABLE ticket_batches ADD smile_is_weekday BOOLEAN;
ALTER TABLE ticket_batches ADD smile_is_child BOOLEAN;
ALTER TABLE ticket_stock ADD smile_seq_no VARCHAR(64);
ALTER TABLE ticket_stock ADD smile_serial VARCHAR(64);
ALTER TABLE ticket_stock ADD smile_barcode VARCHAR(128);
ALTER TABLE tickets ADD smile_is_weekday BOOLEAN;
ALTER TABLE tickets ADD smile_is_child BOOLEAN;
ALTER TABLE tickets ADD source VARCHAR(20);
ALTER TABLE tickets ADD smile_seq_no VARCHAR(64);
ALTER TABLE tickets ADD smile_serial VARCHAR(64);
ALTER TABLE tickets ADD smile_barcode VARCHAR(128);
ALTER TABLE tickets ADD smile_ticket_type_code VARCHAR(64);
ALTER TABLE tickets ADD smile_ticket_type_name VARCHAR(150);
CREATE UNIQUE NULLS DISTINCT INDEX ux_stock_smile_serial ON ticket_stock(smile_serial);
CREATE UNIQUE NULLS DISTINCT INDEX ux_stock_smile_barcode ON ticket_stock(smile_barcode);
