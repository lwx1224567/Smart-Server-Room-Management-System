-- Schema for the one-time source unification runner.
-- UnifiedDataSourceMigration performs the actual transaction, snapshots and row-count checks.
CREATE TABLE IF NOT EXISTS sp_source_migration (
 id VARCHAR(80) PRIMARY KEY,
 applied BOOLEAN NOT NULL DEFAULT FALSE,
 changed_rows BIGINT NOT NULL DEFAULT 0,
 applied_at TIMESTAMP(3) NULL
);
CREATE TABLE IF NOT EXISTS sp_source_backup (
 migration_id VARCHAR(80) NOT NULL,
 table_name VARCHAR(80) NOT NULL,
 record_key VARCHAR(255) NOT NULL,
 payload LONGTEXT NOT NULL,
 PRIMARY KEY(migration_id, table_name, record_key)
);
