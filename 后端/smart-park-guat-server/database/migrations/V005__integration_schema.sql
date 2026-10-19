-- Rules, video configuration and command results use sp_monitor_record kinds RULE/VIDEO/COMMAND.
CREATE TABLE IF NOT EXISTS sp_ingest_batch (
 room_id BIGINT NOT NULL,
 batch_id VARCHAR(100) NOT NULL,
 digest VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(room_id,batch_id)
);
