-- Athena Cognition V1 schema (MySQL 8+)
-- Aligned with cognition-contract-v1.md sections 4 and 11.
-- Sensitive text columns (selected_text, question_text, note, summary) must never
-- be written to application logs.

-- 1. Clue: raw user input, never overwritten by digests or topics (section 4.1)
CREATE TABLE IF NOT EXISTS `cognition_clue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(32) NOT NULL,
    `intent` VARCHAR(32) NOT NULL,
    `relation_type` VARCHAR(32) NULL,
    `help_request_type` VARCHAR(32) NOT NULL,
    `article_id` VARCHAR(128) NULL,
    `article_title` VARCHAR(255) NULL,
    `article_type` INT NULL DEFAULT 100,
    `selected_text` TEXT NOT NULL,
    `question_type` VARCHAR(32) NULL,
    `question_text` VARCHAR(1000) NULL,
    `occurred_at` DATETIME(3) NULL,
    `cycle_relation` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    `severity` INT NULL,
    `resolved` TINYINT(1) NULL,
    `source` VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_ARTICLE',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `suggested_topic_id` VARCHAR(128) NULL,
    `suggested_topic_title` VARCHAR(255) NULL,
    `original_label` VARCHAR(64) NOT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_clue_user_status_created` (`user_id`, `status`, `created_at`),
    KEY `idx_cognition_clue_user_intent_created` (`user_id`, `intent`, `created_at`),
    KEY `idx_cognition_clue_user_article` (`user_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Raw cognition clue marked by the user';

-- 2. Digest: structured draft awaiting user decision (section 4.2)
CREATE TABLE IF NOT EXISTS `cognition_digest` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    `common_point` VARCHAR(500) NULL,
    `possible_relation` VARCHAR(1000) NULL,
    `uncertainty` VARCHAR(1000) NULL,
    `suggested_action` VARCHAR(500) NULL,
    `generator_version` VARCHAR(64) NOT NULL,
    `generated_at` DATETIME(3) NULL,
    `failure_code` VARCHAR(64) NULL,
    `expires_at` DATETIME(3) NULL,
    `version` INT NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_digest_user_status_created` (`user_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-confirmable structured digest';

-- 3. Digest <-> clue membership (sourceClueIds relation table, section 11)
CREATE TABLE IF NOT EXISTS `cognition_digest_clue` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NOT NULL,
    `clue_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_digest_clue` (`digest_id`, `clue_id`),
    KEY `idx_cognition_digest_clue_user_clue` (`user_id`, `clue_id`),
    CONSTRAINT `fk_cognition_digest_clue_digest` FOREIGN KEY (`digest_id`) REFERENCES `cognition_digest` (`id`),
    CONSTRAINT `fk_cognition_digest_clue_clue` FOREIGN KEY (`clue_id`) REFERENCES `cognition_clue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Digest source clue membership';

-- 4. Evidence: independent traceable evidence object (section 4.4)
CREATE TABLE IF NOT EXISTS `cognition_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_type` VARCHAR(32) NOT NULL,
    `source_id` VARCHAR(128) NOT NULL,
    `fact_level` VARCHAR(32) NOT NULL,
    `summary` VARCHAR(1000) NOT NULL,
    `occurred_at` DATETIME(3) NULL,
    `linked_at` DATETIME(3) NOT NULL,
    `active` TINYINT(1) NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_evidence_user_source` (`user_id`, `source_type`, `source_id`),
    KEY `idx_cognition_evidence_user_active_created` (`user_id`, `active`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent evidence linked to digests and topics';

-- 5. Digest <-> evidence link (section 4.4)
CREATE TABLE IF NOT EXISTS `cognition_digest_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NOT NULL,
    `evidence_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_digest_evidence` (`digest_id`, `evidence_id`),
    KEY `idx_cognition_digest_evidence_user_evidence` (`user_id`, `evidence_id`),
    CONSTRAINT `fk_cognition_digest_evidence_digest` FOREIGN KEY (`digest_id`) REFERENCES `cognition_digest` (`id`),
    CONSTRAINT `fk_cognition_digest_evidence_evidence` FOREIGN KEY (`evidence_id`) REFERENCES `cognition_evidence` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Digest evidence membership';

-- 6. Digest task: replaceable fixed or future Agent generation task (section 4.7)
CREATE TABLE IF NOT EXISTS `cognition_digest_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `trigger_type` VARCHAR(32) NOT NULL,
    `generator_version` VARCHAR(64) NOT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(64) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_task_user_status_created` (`user_id`, `status`, `created_at`),
    CONSTRAINT `fk_cognition_task_digest` FOREIGN KEY (`digest_id`) REFERENCES `cognition_digest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fixed or Agent digest generation task';

-- 7. Topic: a question the user explicitly accepted to keep observing (section 4.3)
CREATE TABLE IF NOT EXISTS `cognition_topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_digest_id` BIGINT NOT NULL,
    `title` VARCHAR(120) NOT NULL,
    `domain` VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    `maturity` VARCHAR(32) NOT NULL DEFAULT 'CLUE',
    `user_progress` VARCHAR(32) NOT NULL DEFAULT 'OBSERVING',
    `risk_status` VARCHAR(32) NOT NULL DEFAULT 'NONE',
    `stage_understanding` VARCHAR(1000) NOT NULL,
    `known_facts` JSON NULL,
    `open_questions` JSON NULL,
    `evidence_count` INT NOT NULL DEFAULT 0,
    `article_clue_count` INT NOT NULL DEFAULT 0,
    `body_record_count` INT NOT NULL DEFAULT 0,
    `cycle_count` INT NOT NULL DEFAULT 0,
    `next_action_id` BIGINT NULL,
    `last_updated_at` DATETIME(3) NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_topic_source_digest` (`source_digest_id`),
    KEY `idx_cognition_topic_user_progress_created` (`user_id`, `user_progress`, `created_at`),
    CONSTRAINT `fk_cognition_topic_digest` FOREIGN KEY (`source_digest_id`) REFERENCES `cognition_digest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cognition topic explicitly accepted by the user';

-- 8. Topic <-> evidence link (section 4.4)
CREATE TABLE IF NOT EXISTS `cognition_topic_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `evidence_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_topic_evidence` (`topic_id`, `evidence_id`),
    KEY `idx_cognition_topic_evidence_user_evidence` (`user_id`, `evidence_id`),
    CONSTRAINT `fk_cognition_topic_evidence_topic` FOREIGN KEY (`topic_id`) REFERENCES `cognition_topic` (`id`),
    CONSTRAINT `fk_cognition_topic_evidence_evidence` FOREIGN KEY (`evidence_id`) REFERENCES `cognition_evidence` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Topic evidence membership';

-- 9. Action: next low-burden step for a topic (section 4.5)
CREATE TABLE IF NOT EXISTS `cognition_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `title` VARCHAR(160) NOT NULL,
    `description` VARCHAR(500) NOT NULL,
    `action_type` VARCHAR(32) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `due_at` DATETIME(3) NULL,
    `feedback_options` JSON NULL,
    `version` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cognition_action_user_status_created` (`user_id`, `status`, `created_at`),
    KEY `idx_cognition_action_user_topic` (`user_id`, `topic_id`),
    CONSTRAINT `fk_cognition_action_topic` FOREIGN KEY (`topic_id`) REFERENCES `cognition_topic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Next action suggested for a cognition topic';

-- 10. Action feedback: single user response to an action (section 4.6)
CREATE TABLE IF NOT EXISTS `cognition_action_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `action_id` BIGINT NOT NULL,
    `topic_id` BIGINT NOT NULL,
    `result` VARCHAR(32) NOT NULL,
    `note` VARCHAR(1000) NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `evidence_id` BIGINT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_feedback_action` (`action_id`),
    KEY `idx_cognition_feedback_user_topic_created` (`user_id`, `topic_id`, `created_at`),
    CONSTRAINT `fk_cognition_feedback_action` FOREIGN KEY (`action_id`) REFERENCES `cognition_action` (`id`),
    CONSTRAINT `fk_cognition_feedback_topic` FOREIGN KEY (`topic_id`) REFERENCES `cognition_topic` (`id`),
    CONSTRAINT `fk_cognition_feedback_evidence` FOREIGN KEY (`evidence_id`) REFERENCES `cognition_evidence` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Single user feedback to an action';

-- 11. Decision log: append-only idempotent record of digest decisions (section 11)
CREATE TABLE IF NOT EXISTS `cognition_decision_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `digest_id` BIGINT NOT NULL,
    `decision` VARCHAR(32) NOT NULL,
    `reason` VARCHAR(255) NULL,
    `client_version` INT NULL,
    `request_id` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cognition_decision_digest_decision` (`digest_id`, `decision`),
    KEY `idx_cognition_decision_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_cognition_decision_digest` FOREIGN KEY (`digest_id`) REFERENCES `cognition_digest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only digest decision audit and idempotency record';
