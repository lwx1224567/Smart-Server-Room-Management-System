-- Existing databases only. Idempotent; ConsoleService also performs this rename at startup.
-- Does not change IDs, account relationships, or historical payload contents.
SET @owner_column_sql = IF(
 (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='console_order' AND column_name='owner_id') > 0,
 'ALTER TABLE console_order CHANGE COLUMN owner_id owner_account_id BIGINT NULL',
 'SELECT 1');
PREPARE owner_column_statement FROM @owner_column_sql;
EXECUTE owner_column_statement;
DEALLOCATE PREPARE owner_column_statement;
