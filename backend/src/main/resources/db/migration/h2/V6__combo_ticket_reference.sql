ALTER TABLE products ADD combo_ticket_id BIGINT REFERENCES products(id);
ALTER TABLE products ADD combo_buy_quantity INT;
ALTER TABLE products ADD combo_gift_quantity INT;
ALTER TABLE booking_lines ADD ticket_product_id BIGINT REFERENCES products(id);
ALTER TABLE booking_lines ADD ticket_quantity INT;
ALTER TABLE booking_lines ADD ticket_product_name VARCHAR(150);
-- Legacy combo prices/quantities cannot be inferred from descriptions. Require explicit configuration.
UPDATE products SET active=FALSE WHERE kind='COMBO';
