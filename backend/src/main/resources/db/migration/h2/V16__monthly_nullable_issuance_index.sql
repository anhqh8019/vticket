-- MSSQLServer compatibility treats NULLs as equal unless explicitly overridden.
-- Match the filtered unique index in the SQL Server V15 migration.
DROP INDEX ux_monthly_day_issuance;
CREATE UNIQUE NULLS DISTINCT INDEX ux_monthly_day_issuance ON monthly_pass_days(issued_booking_id);
