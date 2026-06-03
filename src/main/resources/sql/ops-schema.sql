-- AIOps 平台 MySQL 表结构
-- 启动时由 Spring SQL 自动执行(spring.sql.init.mode=always)

CREATE DATABASE IF NOT EXISTS `aiops` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `aiops`;

-- ============ 事故主表 ============
CREATE TABLE IF NOT EXISTS `ops_incident` (
    `id`             VARCHAR(36) NOT NULL,
    `fingerprint`    VARCHAR(64) NOT NULL,
    `source`         VARCHAR(32) NOT NULL,
    `service_name`   VARCHAR(64),
    `severity`       VARCHAR(8),
    `status`         VARCHAR(16)  DEFAULT 'new',
    `occurred_at`    DATETIME,
    `received_at`    DATETIME,
    `resolved_at`    DATETIME,
    `summary`        TEXT,
    `report_path`    VARCHAR(512),
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_fingerprint_time` (`fingerprint`, `occurred_at`),
    KEY `idx_service_status` (`service_name`, `status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故主表';

-- ============ 调查日志(每个 Agent 一条) ============
CREATE TABLE IF NOT EXISTS `ops_investigation_log` (
    `id`               BIGINT          NOT NULL AUTO_INCREMENT,
    `incident_id`      VARCHAR(36)     NOT NULL,
    `agent_name`       VARCHAR(64)     NOT NULL,
    `output_text`      MEDIUMTEXT,
    `duration_ms`      BIGINT,
    `prompt_tokens`    INT,
    `completion_tokens` INT,
    `total_tokens`     INT,
    `created_at`       DATETIME        DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_incident` (`incident_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调查日志';

-- ============ 动作审计 ============
CREATE TABLE IF NOT EXISTS `ops_action_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `incident_id`      VARCHAR(36)  NOT NULL,
    `action_type`      VARCHAR(32)  NOT NULL,
    `target`           VARCHAR(256),
    `triggered_by`     VARCHAR(64),
    `confirmed_by`     VARCHAR(64),
    `triggered_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `executed_at`      DATETIME,
    `result_status`    VARCHAR(16),
    `result_message`   TEXT,
    PRIMARY KEY (`id`),
    KEY `idx_incident` (`incident_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作审计';
