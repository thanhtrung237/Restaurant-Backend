-- Run once before deploying when Hibernate is not updating the schema.
ALTER TABLE restaurant ADD COLUMN operational_permissions TEXT NULL;
