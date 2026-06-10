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
    `thread_id`      VARCHAR(64),     -- Agent 流水线 threadId, 用于 MysqlSaver 续跑
    `occurred_at`    DATETIME,
    `received_at`    DATETIME,
    `resolved_at`    DATETIME,
    `summary`        TEXT,
    `report_path`    VARCHAR(512),
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_fingerprint_time` (`fingerprint`, `occurred_at`),
    KEY `idx_service_status` (`service_name`, `status`),
    KEY `idx_thread_id` (`thread_id`),
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

-- ============ 知识库 (MySQL 是主库,Milvus 是 RAG 索引,双写) ============
-- source: manual(人工录入) / auto(流水线自动入库) / imported(批量导入)
-- status: active(参与 RAG) / archived(保留但不参与检索)
CREATE TABLE IF NOT EXISTS `ops_knowledge_base` (
    `id`             VARCHAR(64)  NOT NULL,
    `service_name`   VARCHAR(64),
    `error_class`    VARCHAR(256),
    `severity`       VARCHAR(8),
    `title`          VARCHAR(256) NOT NULL,
    `summary`        TEXT,
    `resolution`     MEDIUMTEXT,
    `tags`           VARCHAR(256),                       -- 逗号分隔
    `source`         VARCHAR(16)  DEFAULT 'manual',
    `status`         VARCHAR(16)  DEFAULT 'active',
    `created_by`     VARCHAR(64),
    `updated_by`     VARCHAR(64),
    `occurred_at`    DATETIME,
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kb_service_status` (`service_name`, `status`),
    KEY `idx_kb_status_updated` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库 (RAG 数据源)';

-- ============ 动作审计 (HITL 工单) ============
-- status 状态机: PENDING -> APPROVED -> EXECUTED / FAILED
--                       \-> REJECTED
CREATE TABLE IF NOT EXISTS `ops_action_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `incident_id`        VARCHAR(36)  NOT NULL,
    `action_type`        VARCHAR(32)  NOT NULL,
    `target`             VARCHAR(256),
    `proposal_payload`   TEXT,
    `rationale`          TEXT,
    `status`             VARCHAR(16)  DEFAULT 'PENDING',
    `approve_token`      VARCHAR(64),
    `triggered_by`       VARCHAR(64),
    `confirmed_by`       VARCHAR(64),
    `triggered_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `confirmed_at`       DATETIME,
    `executed_at`        DATETIME,
    `result_status`      VARCHAR(16),
    `result_message`     TEXT,
    PRIMARY KEY (`id`),
    KEY `idx_incident` (`incident_id`),
    KEY `idx_status_created` (`status`, `triggered_at`),
    UNIQUE KEY `uk_approve_token` (`approve_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作审计 / HITL 工单';
