ALTER TABLE products ADD detail_content NVARCHAR(12000) NULL;
ALTER TABLE products ADD detail_image VARBINARY(2097152) NULL;
ALTER TABLE products ADD detail_image_type VARCHAR(30) NULL;
ALTER TABLE products ADD detail_image_version VARCHAR(36) NULL;
