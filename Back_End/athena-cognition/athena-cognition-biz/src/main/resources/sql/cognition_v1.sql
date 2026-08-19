-- Athena Cognition V1 schema (MySQL 8+)
-- Sensitive text columns must never be written to application logs.

CREATE TABLE IF NOT EXISTS `tb_cognition_clue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `clue_type` VARCHAR(32) NOT NULL,
    `mark_intent` VARCHAR(32) NULL,
    `relation_detail` VARCHAR(32) NULL,
    `desired_help` VARCHAR(32) NULL,
    `article_id` VARCHAR(128) NULL,
    `article_title` VARCHAR(255) NULL,
    `source_name` VARCHAR(128) NULL,
    `excerpt` TEXT NULL,
    `question_type` VARCHAR(64) NULL,
    `question_text` VARCHAR(1000) NULL,
    `body_record_id` BIGINT NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_clue_user_status_id` (`user_id`, `status`, `id`),
    KEY `idx_cognition_clue_user_intent_id` (`user_id`, `mark_intent`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-confirmed cognition input or low-weight content clue';

CREATE TABLE IF NOT EXISTS `tb_cognition_digest_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `generator_type` VARCHAR(32) NOT NULL DEFAULT 'FIXED_V1',
    `attempt_count` INT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(64) NULL,
    `failure_message` VARCHAR(255) NULL,
    `started_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_task_user_status_id` (`user_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Replaceable fixed or Agent digest generation task';

CREATE TABLE IF NOT EXISTS `tb_cognition_digest_task_clue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_task_id` BIGINT NOT NULL,
    `clue_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_task_clue` (`digest_task_id`, `clue_id`),
    KEY `idx_cognition_task_clue_user_clue` (`user_id`, `clue_id`),
    CONSTRAINT `fk_cognition_task_clue_task` FOREIGN KEY (`digest_task_id`) REFERENCES `tb_cognition_digest_task` (`id`),
    CONSTRAINT `fk_cognition_task_clue_clue` FOREIGN KEY (`clue_id`) REFERENCES `tb_cognition_clue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable task input membership';

CREATE TABLE IF NOT EXISTS `tb_cognition_digest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_task_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    `title` VARCHAR(120) NOT NULL,
    `common_point` VARCHAR(500) NOT NULL,
    `possible_link` VARCHAR(1000) NOT NULL,
    `uncertainty` VARCHAR(1000) NOT NULL,
    `suggested_action` VARCHAR(500) NOT NULL,
    `generator_type` VARCHAR(32) NOT NULL,
    `generator_version` VARCHAR(32) NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `decided_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_digest_task` (`digest_task_id`),
    KEY `idx_cognition_digest_user_status_id` (`user_id`, `status`, `id`),
    CONSTRAINT `fk_cognition_digest_task` FOREIGN KEY (`digest_task_id`) REFERENCES `tb_cognition_digest_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-confirmable structured digest';

CREATE TABLE IF NOT EXISTS `tb_cognition_topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_digest_id` BIGINT NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `summary` VARCHAR(1000) NOT NULL,
    `uncertainty` VARCHAR(1000) NOT NULL,
    `maturity` VARCHAR(32) NOT NULL DEFAULT 'CLUE',
    `progress` VARCHAR(32) NOT NULL DEFAULT 'FOLLOWING',
    `risk_status` VARCHAR(32) NOT NULL DEFAULT 'NONE',
    `current_version` INT NOT NULL DEFAULT 1,
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_topic_digest` (`source_digest_id`),
    KEY `idx_cognition_topic_user_progress_id` (`user_id`, `progress`, `id`),
    KEY `idx_cognition_topic_user_risk_id` (`user_id`, `risk_status`, `id`),
    CONSTRAINT `fk_cognition_topic_digest` FOREIGN KEY (`source_digest_id`) REFERENCES `tb_cognition_digest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A specific body question explicitly accepted by the user';

CREATE TABLE IF NOT EXISTS `tb_cognition_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NOT NULL,
    `topic_id` BIGINT NULL,
    `clue_id` BIGINT NOT NULL,
    `evidence_level` VARCHAR(16) NOT NULL,
    `evidence_role` VARCHAR(32) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_evidence_digest_clue` (`digest_id`, `clue_id`),
    KEY `idx_cognition_evidence_user_topic` (`user_id`, `topic_id`, `id`),
    CONSTRAINT `fk_cognition_evidence_digest` FOREIGN KEY (`digest_id`) REFERENCES `tb_cognition_digest` (`id`),
    CONSTRAINT `fk_cognition_evidence_topic` FOREIGN KEY (`topic_id`) REFERENCES `tb_cognition_topic` (`id`),
    CONSTRAINT `fk_cognition_evidence_clue` FOREIGN KEY (`clue_id`) REFERENCES `tb_cognition_clue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Trace from digest/topic statements to original user input';

CREATE TABLE IF NOT EXISTS `tb_cognition_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `title` VARCHAR(160) NOT NULL,
    `instruction` VARCHAR(500) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `due_at` DATETIME(3) NULL,
    `completed_at` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_action_user_status_id` (`user_id`, `status`, `id`),
    KEY `idx_cognition_action_user_topic_id` (`user_id`, `topic_id`, `id`),
    CONSTRAINT `fk_cognition_action_topic` FOREIGN KEY (`topic_id`) REFERENCES `tb_cognition_topic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One low-burden next action for a cognition topic';

CREATE TABLE IF NOT EXISTS `tb_cognition_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `action_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `accuracy` VARCHAR(32) NOT NULL,
    `completed` TINYINT(1) NOT NULL,
    `note` VARCHAR(1000) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_feedback_action` (`action_id`),
    KEY `idx_cognition_feedback_user_topic_id` (`user_id`, `topic_id`, `id`),
    CONSTRAINT `fk_cognition_feedback_action` FOREIGN KEY (`action_id`) REFERENCES `tb_cognition_action` (`id`),
    CONSTRAINT `fk_cognition_feedback_topic` FOREIGN KEY (`topic_id`) REFERENCES `tb_cognition_topic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Single user response to an action';

CREATE TABLE IF NOT EXISTS `tb_cognition_digest_decision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NOT NULL,
    `decision` VARCHAR(32) NOT NULL,
    `reason_code` VARCHAR(64) NULL,
    `topic_id` BIGINT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_decision_digest` (`digest_id`),
    KEY `idx_cognition_decision_user_id` (`user_id`, `id`),
    CONSTRAINT `fk_cognition_decision_digest` FOREIGN KEY (`digest_id`) REFERENCES `tb_cognition_digest` (`id`),
    CONSTRAINT `fk_cognition_decision_topic` FOREIGN KEY (`topic_id`) REFERENCES `tb_cognition_topic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only user confirmation audit';

CREATE TABLE IF NOT EXISTS `tb_cognition_topic_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `version_no` INT NOT NULL,
    `summary` VARCHAR(1000) NOT NULL,
    `uncertainty` VARCHAR(1000) NOT NULL,
    `maturity` VARCHAR(32) NOT NULL,
    `progress` VARCHAR(32) NOT NULL,
    `risk_status` VARCHAR(32) NOT NULL,
    `change_reason` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_topic_version` (`topic_id`, `version_no`),
    KEY `idx_cognition_topic_version_user_topic` (`user_id`, `topic_id`, `version_no`),
    CONSTRAINT `fk_cognition_topic_version_topic` FOREIGN KEY (`topic_id`) REFERENCES `tb_cognition_topic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only explanation and state history';

CREATE TABLE IF NOT EXISTS `tb_cognition_idempotency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(64) NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `request_hash` CHAR(64) NOT NULL,
    `resource_type` VARCHAR(32) NULL,
    `resource_id` BIGINT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expires_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_idempotency` (`user_id`, `operation`, `idempotency_key`),
    KEY `idx_cognition_idempotency_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mutation deduplication without retaining raw request text';
