CREATE TABLE ticket_batches (id VARCHAR(36) PRIMARY KEY,code VARCHAR(40) NOT NULL UNIQUE,product_id BIGINT NOT NULL REFERENCES products(id),quantity INT NOT NULL CHECK(quantity>0),valid_from DATE NOT NULL,valid_until DATE NOT NULL,active BIT NOT NULL,source VARCHAR(20) NOT NULL,created_at DATETIMEOFFSET NOT NULL,created_by VARCHAR(100) NOT NULL);
CREATE TABLE ticket_stock (id VARCHAR(36) PRIMARY KEY,batch_id VARCHAR(36) NOT NULL REFERENCES ticket_batches(id),product_id BIGINT NOT NULL REFERENCES products(id),code VARCHAR(64) NOT NULL UNIQUE,qr_token VARCHAR(36) NOT NULL UNIQUE,state VARCHAR(16) NOT NULL,reserved_order_id VARCHAR(36),reserved_until DATETIMEOFFSET,sold_order_id VARCHAR(36) REFERENCES bookings(id),sold_at DATETIMEOFFSET,used_at DATETIMEOFFSET);
CREATE INDEX ix_stock_allocate ON ticket_stock(product_id,state,batch_id);
CREATE INDEX ix_stock_reservation ON ticket_stock(reserved_order_id);
CREATE INDEX ix_stock_batch_state ON ticket_stock(batch_id,state);
GO
ALTER TABLE tickets ADD stock_id VARCHAR(36) REFERENCES ticket_stock(id);
ALTER TABLE tickets ADD ticket_code VARCHAR(64);
ALTER TABLE tickets ADD batch_code VARCHAR(40);
GO
CREATE UNIQUE INDEX ux_tickets_stock ON tickets(stock_id) WHERE stock_id IS NOT NULL;
