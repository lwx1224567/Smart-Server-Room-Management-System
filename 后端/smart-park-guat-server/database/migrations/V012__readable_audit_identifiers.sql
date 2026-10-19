-- Public audit identifiers: RZ log, QQ request, PC ingest batch, BF backup, HY session.
-- Original primary/association IDs, token_hash and backup payloads remain unchanged.
CREATE TABLE IF NOT EXISTS sp_readable_day_sequence (
  number_kind VARCHAR(8) NOT NULL,
  number_day DATE NOT NULL,
  sequence_value BIGINT NOT NULL,
  PRIMARY KEY (number_kind, number_day)
);
CREATE TABLE IF NOT EXISTS sp_readable_identifier (
  number_kind VARCHAR(8) NOT NULL,
  identity_hash CHAR(64) NOT NULL,
  display_code VARCHAR(40) NOT NULL,
  PRIMARY KEY (number_kind, identity_hash),
  UNIQUE KEY uq_readable_code (display_code)
);
-- ReadableIdentifierService checks information_schema before adding unique nullable
-- VARCHAR(40) columns: sp_ingest_batch.batch_code, console_session.session_code,
-- and sp_source_backup.backup_code. It backfills them on ApplicationReadyEvent.
-- Log payloads receive logCode/requestCode; nested responses receive requestCode.
