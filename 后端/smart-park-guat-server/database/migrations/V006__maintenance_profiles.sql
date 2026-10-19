-- Additive migration; also applied idempotently by ConsoleService at startup.
CREATE TABLE IF NOT EXISTS console_person_profile (account_id BIGINT PRIMARY KEY,biography VARCHAR(1000) NOT NULL,skills TEXT NOT NULL);
