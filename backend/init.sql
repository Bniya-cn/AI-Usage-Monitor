CREATE DATABASE IF NOT EXISTS ai_usage_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_usage_monitor;


CREATE TABLE IF NOT EXISTS usage_snapshots (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    service     VARCHAR(50) NOT NULL,
    model       VARCHAR(100),
    date_str    VARCHAR(10) NOT NULL,
    tokens_in   INT DEFAULT 0,
    tokens_out  INT DEFAULT 0,
    requests    INT DEFAULT 0,
    cost_usd    DOUBLE DEFAULT 0.0,
    extra_json  TEXT,
    synced_at   VARCHAR(50) NOT NULL,
    UNIQUE KEY ukey_service_model_date (service, model, date_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS auth_status (
    service     VARCHAR(50) PRIMARY KEY,
    is_auth     INT DEFAULT 0,
    last_check  VARCHAR(50),
    error_msg   TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS config_settings (
    cfg_key     VARCHAR(50) PRIMARY KEY,
    cfg_value   TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS api_keys (
    service     VARCHAR(50) PRIMARY KEY,
    api_key     VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
