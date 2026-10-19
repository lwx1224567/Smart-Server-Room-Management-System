-- Public work-order numbers; existing console_order.id and notice order_id are unchanged.
CREATE TABLE IF NOT EXISTS sp_order_day_sequence (
  order_day DATE PRIMARY KEY,
  sequence_value BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sp_order_number (
  order_id VARCHAR(80) PRIMARY KEY,
  order_code VARCHAR(40) NOT NULL,
  UNIQUE KEY uq_order_code (order_code)
);
-- ConsoleService assigns GD + creation date (Asia/Shanghai) + daily sequence on startup.
