-- Public alarm number mapping. UUID identities and existing work-order references stay unchanged.
CREATE TABLE IF NOT EXISTS sp_alarm_day_sequence (
  alarm_day DATE PRIMARY KEY,
  sequence_value BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sp_alarm_number (
  room_id BIGINT NOT NULL,
  alarm_id VARCHAR(100) NOT NULL,
  alarm_code VARCHAR(40) NOT NULL,
  PRIMARY KEY (room_id, alarm_id),
  UNIQUE KEY uq_alarm_code (alarm_code)
);
-- MonitoringService assigns historical codes on startup, in creation-time order.
-- Format: GJ + creation date in Asia/Shanghai + daily sequence (at least four digits).
