-- Run in the application's MySQL schema before deploying the backend.
-- Nullable fields preserve existing accounts and platform onboarding.
SET @staff_contact_ddl = CONCAT_WS(', ',
  IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'phone'),
    NULL, 'ADD COLUMN phone VARCHAR(10) NULL'),
  IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'hire_date'),
    NULL, 'ADD COLUMN hire_date DATE NULL'),
  IF(EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account'
        AND INDEX_NAME = 'uk_account_restaurant_phone'),
    NULL, 'ADD CONSTRAINT uk_account_restaurant_phone UNIQUE (restaurant_id, phone)')
);
SET @staff_contact_ddl = IF(@staff_contact_ddl = '',
  'SELECT ''Staff contact schema already up to date''',
  CONCAT('ALTER TABLE account ', @staff_contact_ddl));
PREPARE staff_contact_migration FROM @staff_contact_ddl;
EXECUTE staff_contact_migration;
DEALLOCATE PREPARE staff_contact_migration;
