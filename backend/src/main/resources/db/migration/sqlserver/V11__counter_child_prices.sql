ALTER TABLE products ADD child_price DECIMAL(18,0) NULL;
ALTER TABLE products ADD child_weekend_price DECIMAL(18,0) NULL;
ALTER TABLE products ADD child_holiday_price DECIMAL(18,0) NULL;
ALTER TABLE booking_lines ADD child BIT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD child BIT NOT NULL DEFAULT 0;
