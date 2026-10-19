-- No demo rows. Collector/import jobs write actual records to these tables.
CREATE TABLE IF NOT EXISTS sp_monitor_record (
 room_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL, id VARCHAR(100) NOT NULL,
 payload LONGTEXT NOT NULL, PRIMARY KEY(room_id,kind,id)
);
CREATE TABLE IF NOT EXISTS sp_reading (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, room_id BIGINT NOT NULL,
 entity_type VARCHAR(16) NOT NULL, entity_id VARCHAR(100) NOT NULL,
 metric VARCHAR(64) NOT NULL, value DOUBLE NOT NULL, unit VARCHAR(24) NOT NULL,
 observed_at TIMESTAMP(3) NOT NULL, quality VARCHAR(16) NOT NULL,
 source VARCHAR(100) NOT NULL, record_origin VARCHAR(8) NOT NULL,
 KEY ix_reading(room_id,entity_type,entity_id,metric,observed_at)
);
CREATE TABLE IF NOT EXISTS sp_energy_sample (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, room_id BIGINT NOT NULL,
 category VARCHAR(24) NOT NULL, cabinet_id BIGINT NULL, kwh DOUBLE NOT NULL,
 observed_at TIMESTAMP(3) NOT NULL, quality VARCHAR(16) NOT NULL,
 record_origin VARCHAR(8) NOT NULL, source VARCHAR(100) NOT NULL,
 KEY ix_energy(room_id,observed_at)
);
