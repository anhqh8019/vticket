-- Preserve existing batches, stock and paid tickets; Smile integration itself is deferred.
ALTER TABLE ticket_batches ADD smile_ticket_type_code VARCHAR(64);
ALTER TABLE ticket_batches ADD smile_ticket_type_name NVARCHAR(150);
ALTER TABLE ticket_batches ADD smile_is_weekday BIT;
ALTER TABLE ticket_batches ADD smile_is_child BIT;
ALTER TABLE ticket_stock ADD smile_seq_no VARCHAR(64);
ALTER TABLE ticket_stock ADD smile_serial VARCHAR(64);
ALTER TABLE ticket_stock ADD smile_barcode VARCHAR(128);
ALTER TABLE tickets ADD smile_is_weekday BIT;
ALTER TABLE tickets ADD smile_is_child BIT;
ALTER TABLE tickets ADD source VARCHAR(20);
ALTER TABLE tickets ADD smile_seq_no VARCHAR(64);
ALTER TABLE tickets ADD smile_serial VARCHAR(64);
ALTER TABLE tickets ADD smile_barcode VARCHAR(128);
ALTER TABLE tickets ADD smile_ticket_type_code VARCHAR(64);
ALTER TABLE tickets ADD smile_ticket_type_name NVARCHAR(150);
GO
CREATE UNIQUE INDEX ux_stock_smile_serial ON ticket_stock(smile_serial) WHERE smile_serial IS NOT NULL;
CREATE UNIQUE INDEX ux_stock_smile_barcode ON ticket_stock(smile_barcode) WHERE smile_barcode IS NOT NULL;
