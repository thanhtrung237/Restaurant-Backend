-- MySQL 8.4. Run in the restaurant schema before deploying the new backend.
-- Replace Hibernate's old type whitelist when it exists. Fresh schemas already
-- allow TABLE_OPEN_REQUESTED, so this script is safe to run again.
SELECT GROUP_CONCAT(CONCAT('DROP CHECK `', REPLACE(tc.CONSTRAINT_NAME, '`', '``'), '`'))
INTO @old_notification_type_checks
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = DATABASE()
  AND tc.TABLE_NAME = 'staff_notification'
  AND tc.CONSTRAINT_TYPE = 'CHECK'
  AND REGEXP_LIKE(cc.CHECK_CLAUSE, '^[[:space:]()]*`?type`?[[:space:]]+in[[:space:]]*[(][^)]*[)][[:space:]()]*$', 'i')
  AND LOCATE('NEW_ORDER', UPPER(cc.CHECK_CLAUSE)) > 0
  AND LOCATE('TABLE_OPEN_REQUESTED', UPPER(cc.CHECK_CLAUSE)) = 0;

SET @notification_type_ddl = IF(
  @old_notification_type_checks IS NULL,
  'SELECT ''No legacy staff notification type check to replace'' AS status',
  CONCAT('ALTER TABLE `staff_notification` ', @old_notification_type_checks,
    ', ADD CHECK (`type` IN (''NEW_ORDER'', ''PAYMENT_REQUESTED'', ''STAFF_CALL'',',
    ' ''PAYMENT_QR_OPENED'', ''BANK_PAYMENT_VERIFIED'', ''DISCOUNT_APPROVAL'', ''TABLE_OPEN_REQUESTED''))')
);
PREPARE notification_type_migration FROM @notification_type_ddl;
EXECUTE notification_type_migration;
DEALLOCATE PREPARE notification_type_migration;
