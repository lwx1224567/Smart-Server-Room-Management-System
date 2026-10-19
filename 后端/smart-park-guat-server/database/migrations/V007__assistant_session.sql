-- Additive; AssistantAgentService also creates this table idempotently at startup.
CREATE TABLE IF NOT EXISTS console_assistant_session (
 account_id BIGINT NOT NULL,
 room_id BIGINT NOT NULL,
 payload LONGTEXT NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 PRIMARY KEY(account_id, room_id)
);
