CREATE TABLE monthly_plans (
 tier VARCHAR(16) PRIMARY KEY CHECK(tier IN ('SILVER','GOLD','PLATINUM')),
 ticket_product_id BIGINT NOT NULL REFERENCES products(id), meal_product_id BIGINT REFERENCES products(id),
 weekday_price DECIMAL(18,0) NOT NULL CHECK(weekday_price>=0), weekend_price DECIMAL(18,0) NOT NULL CHECK(weekend_price>=0), active BIT NOT NULL
);
CREATE TABLE monthly_passes (
 booking_id VARCHAR(36) PRIMARY KEY REFERENCES bookings(id), qr_token VARCHAR(36) NOT NULL UNIQUE,
 tier VARCHAR(16) NOT NULL, ticket_product_id BIGINT NOT NULL REFERENCES products(id), meal_product_id BIGINT REFERENCES products(id),
 holder_name NVARCHAR(150) NOT NULL, ticket_name NVARCHAR(150) NOT NULL, meal_name NVARCHAR(150), created_at DATETIMEOFFSET(6) NOT NULL
);
CREATE TABLE monthly_pass_days (
 booking_id VARCHAR(36) NOT NULL REFERENCES monthly_passes(booking_id), visit_date DATE NOT NULL,
 price DECIMAL(18,0) NOT NULL CHECK(price>=0), day_type VARCHAR(16) NOT NULL,
 issued_booking_id VARCHAR(36) REFERENCES bookings(id), issued_at DATETIMEOFFSET(6), issued_by VARCHAR(100),
 PRIMARY KEY(booking_id,visit_date)
);
CREATE INDEX ix_monthly_pass_created ON monthly_passes(created_at);
CREATE UNIQUE INDEX ux_monthly_day_issuance ON monthly_pass_days(issued_booking_id) WHERE issued_booking_id IS NOT NULL;
