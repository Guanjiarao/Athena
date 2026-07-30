-- Athena Insight 初始化建表脚本
-- 作用：创建 topic、特征快照、洞察结果等基础表，并写入最小联调演示数据
-- 执行前建议先确认数据库版本与 JSON 字段支持情况
-- 若当前 MySQL 版本对 JSON 支持不稳定，可改为 LONGTEXT

START TRANSACTION;

-- =========================
-- 1. topic 主表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `topic_code` VARCHAR(64) NOT NULL COMMENT 'topic编码',
    `topic_name` VARCHAR(64) NOT NULL COMMENT 'topic名称',
    `parent_id` BIGINT NULL COMMENT '父topicID',
    `topic_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'topic类型：1内容类 2健康类 3通用类',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `description` VARCHAR(255) NULL COMMENT '描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_topic_code` (`topic_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_topic_type` (`topic_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='topic主表';

-- =========================
-- 2. 内容与 topic 关系表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_note_topic_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id` BIGINT NOT NULL COMMENT '内容ID',
    `topic_id` BIGINT NOT NULL COMMENT 'topic ID',
    `weight` DECIMAL(6,4) NOT NULL DEFAULT 1.0000 COMMENT 'topic权重',
    `source_type` TINYINT NOT NULL DEFAULT 1 COMMENT '来源类型：1人工 2规则 3系统',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_topic` (`note_id`, `topic_id`),
    KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容与topic关系表';

-- =========================
-- 3. record 与 topic 映射规则表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_record_topic_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `mode_type` TINYINT NOT NULL COMMENT '模式类型：1经期 2备孕 3怀孕',
    `record_item_id` INT NOT NULL COMMENT '记录项ID',
    `record_value_pattern` VARCHAR(128) NULL COMMENT '记录值匹配规则，可空',
    `topic_id` BIGINT NOT NULL COMMENT 'topic ID',
    `weight` DECIMAL(6,4) NOT NULL DEFAULT 1.0000 COMMENT '映射权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mode_item` (`mode_type`, `record_item_id`),
    KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='record与topic映射规则表';

-- =========================
-- 4. 用户特征快照表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_user_feature_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `base_feature_json` JSON NULL COMMENT '基础特征JSON',
    `behavior_feature_json` JSON NULL COMMENT '行为特征JSON',
    `health_feature_json` JSON NULL COMMENT '健康特征JSON',
    `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `feature_version` INT NOT NULL DEFAULT 1 COMMENT '特征版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_generated_at` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户特征快照表';

-- =========================
-- 5. 内容特征快照表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_note_feature` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id` BIGINT NOT NULL COMMENT '内容ID',
    `type` TINYINT NOT NULL COMMENT '内容类型：0科普 1图文 2视频',
    `author_id` BIGINT NOT NULL COMMENT '作者ID',
    `title` VARCHAR(255) NULL COMMENT '内容标题',
    `cover_url` VARCHAR(512) NULL COMMENT '封面地址',
    `channel_id` INT NULL COMMENT '栏目ID，保留原业务维度',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '内容状态',
    `topic_feature_json` JSON NULL COMMENT '内容topic特征JSON',
    `quality_score` DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '质量分',
    `hot_score` DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '热度分',
    `feature_version` INT NOT NULL DEFAULT 1 COMMENT '特征版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_id` (`note_id`),
    KEY `idx_type_status` (`type`, `status`),
    KEY `idx_hot_score` (`hot_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容特征快照表';

-- =========================
-- 6. 用户洞察结果表
-- =========================
CREATE TABLE IF NOT EXISTS `tb_user_insight` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `health_focus_json` JSON NULL COMMENT '健康关注点JSON',
    `content_focus_json` JSON NULL COMMENT '内容关注点JSON',
    `risk_tags_json` JSON NULL COMMENT '风险标签JSON',
    `recommendation_reasons_json` JSON NULL COMMENT '推荐原因JSON',
    `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `insight_version` INT NOT NULL DEFAULT 1 COMMENT '洞察版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户洞察结果表';

-- =========================
-- 7. 演示数据：topic
-- =========================
INSERT INTO `tb_topic` (`id`, `topic_code`, `topic_name`, `parent_id`, `topic_type`, `status`, `sort`, `description`)
VALUES
    (1, 'menstrual_care', '经期护理', NULL, 1, 1, 10, '围绕经期日常护理的内容主题'),
    (2, 'dysmenorrhea_relief', '痛经缓解', 1, 2, 1, 20, '围绕腹痛缓解、热敷与动作建议的健康主题'),
    (3, 'sleep_regulation', '睡眠调节', NULL, 1, 1, 30, '围绕睡眠质量改善的内容主题'),
    (4, 'emotion_management', '情绪调节', NULL, 1, 1, 40, '围绕情绪波动和压力管理的内容主题'),
    (5, 'diet_management', '饮食管理', NULL, 1, 1, 50, '围绕经期和女性健康饮食建议的内容主题'),
    (6, 'beginner_guide', '新手入门', NULL, 3, 1, 60, '适合新用户的通用知识主题')
ON DUPLICATE KEY UPDATE
    `topic_name` = VALUES(`topic_name`),
    `parent_id` = VALUES(`parent_id`),
    `topic_type` = VALUES(`topic_type`),
    `status` = VALUES(`status`),
    `sort` = VALUES(`sort`),
    `description` = VALUES(`description`);

-- =========================
-- 8. 演示数据：record 与 topic 映射
-- =========================
INSERT INTO `tb_record_topic_relation` (`id`, `mode_type`, `record_item_id`, `record_value_pattern`, `topic_id`, `weight`)
VALUES
    (1, 1, 101, NULL, 2, 1.0000),
    (2, 1, 102, NULL, 3, 0.9000),
    (3, 1, 103, NULL, 4, 0.9000),
    (4, 1, 104, NULL, 5, 0.7000)
ON DUPLICATE KEY UPDATE
    `record_value_pattern` = VALUES(`record_value_pattern`),
    `topic_id` = VALUES(`topic_id`),
    `weight` = VALUES(`weight`),
    `update_time` = CURRENT_TIMESTAMP;

-- =========================
-- 9. 演示数据：内容特征
-- =========================
INSERT INTO `tb_note_feature` (`id`, `note_id`, `type`, `author_id`, `title`, `cover_url`, `channel_id`, `status`, `topic_feature_json`, `quality_score`, `hot_score`, `feature_version`)
VALUES
    (1, 10001, 0, 2001, '经期腹痛应该怎么缓解', 'https://example.com/insight/cover-10001.jpg', 1, 1, JSON_ARRAY('经期护理', '痛经缓解'), 9.2000, 8.9000, 1),
    (2, 10002, 0, 2002, '经期睡不好如何调整作息', 'https://example.com/insight/cover-10002.jpg', 1, 1, JSON_ARRAY('睡眠调节', '经期护理'), 8.8000, 8.3000, 1),
    (3, 10003, 1, 3001, '姨妈期这三种热敷方式真的有用', 'https://example.com/insight/cover-10003.jpg', 2, 1, JSON_ARRAY('痛经缓解', '经期护理'), 8.6000, 9.5000, 1),
    (4, 10004, 1, 3002, '经期低落时我会这样给自己减压', 'https://example.com/insight/cover-10004.jpg', 3, 1, JSON_ARRAY('情绪调节', '经期护理'), 8.3000, 8.7000, 1),
    (5, 10005, 2, 4001, '缓解姨妈痛的三个拉伸动作', 'https://example.com/insight/cover-10005.jpg', 2, 1, JSON_ARRAY('痛经缓解', '新手入门'), 8.9000, 9.7000, 1),
    (6, 10006, 2, 4002, '经期饮食清单：这几类食物更友好', 'https://example.com/insight/cover-10006.jpg', 4, 1, JSON_ARRAY('饮食管理', '经期护理'), 8.5000, 8.2000, 1)
ON DUPLICATE KEY UPDATE
    `type` = VALUES(`type`),
    `author_id` = VALUES(`author_id`),
    `title` = VALUES(`title`),
    `cover_url` = VALUES(`cover_url`),
    `channel_id` = VALUES(`channel_id`),
    `status` = VALUES(`status`),
    `topic_feature_json` = VALUES(`topic_feature_json`),
    `quality_score` = VALUES(`quality_score`),
    `hot_score` = VALUES(`hot_score`),
    `feature_version` = VALUES(`feature_version`),
    `update_time` = CURRENT_TIMESTAMP;

-- =========================
-- 10. 演示数据：内容与 topic 关系
-- =========================
INSERT INTO `tb_note_topic_relation` (`id`, `note_id`, `topic_id`, `weight`, `source_type`)
VALUES
    (1, 10001, 1, 0.9000, 1),
    (2, 10001, 2, 1.0000, 1),
    (3, 10002, 1, 0.7000, 1),
    (4, 10002, 3, 1.0000, 1),
    (5, 10003, 1, 0.8000, 1),
    (6, 10003, 2, 1.0000, 1),
    (7, 10004, 1, 0.6000, 1),
    (8, 10004, 4, 1.0000, 1),
    (9, 10005, 2, 1.0000, 1),
    (10, 10005, 6, 0.5000, 1),
    (11, 10006, 1, 0.7000, 1),
    (12, 10006, 5, 1.0000, 1)
ON DUPLICATE KEY UPDATE
    `weight` = VALUES(`weight`),
    `source_type` = VALUES(`source_type`);

-- =========================
-- 11. 演示数据：用户特征快照
-- =========================
INSERT INTO `tb_user_feature_snapshot` (`id`, `user_id`, `base_feature_json`, `behavior_feature_json`, `health_feature_json`, `generated_at`, `feature_version`)
VALUES
    (
        1,
        100001,
        JSON_OBJECT('age', 26, 'modeType', 1, 'city', 'Hangzhou'),
        JSON_OBJECT('preferredTypes', JSON_ARRAY(0, 1, 2), 'activeTopics', JSON_ARRAY('痛经缓解', '睡眠调节', '情绪调节')),
        JSON_OBJECT('symptomTopics', JSON_ARRAY('痛经缓解', '睡眠调节'), 'currentStage', 'menstrual'),
        CURRENT_TIMESTAMP,
        1
    )
ON DUPLICATE KEY UPDATE
    `base_feature_json` = VALUES(`base_feature_json`),
    `behavior_feature_json` = VALUES(`behavior_feature_json`),
    `health_feature_json` = VALUES(`health_feature_json`),
    `generated_at` = VALUES(`generated_at`),
    `feature_version` = VALUES(`feature_version`),
    `update_time` = CURRENT_TIMESTAMP;

-- =========================
-- 12. 演示数据：用户洞察结果
-- =========================
INSERT INTO `tb_user_insight` (`id`, `user_id`, `health_focus_json`, `content_focus_json`, `risk_tags_json`, `recommendation_reasons_json`, `generated_at`, `insight_version`)
VALUES
    (
        1,
        1024,
        JSON_ARRAY('痛经缓解', '睡眠调节'),
        JSON_ARRAY('经期护理', '情绪调节', '饮食管理'),
        JSON_ARRAY('经期腹痛', '睡眠不足'),
        JSON_ARRAY('近期你更关注经期不适缓解内容', '系统优先推荐与当前状态相关的高热度内容'),
        CURRENT_TIMESTAMP,
        1
    )
ON DUPLICATE KEY UPDATE
    `health_focus_json` = VALUES(`health_focus_json`),
    `content_focus_json` = VALUES(`content_focus_json`),
    `risk_tags_json` = VALUES(`risk_tags_json`),
    `recommendation_reasons_json` = VALUES(`recommendation_reasons_json`),
    `generated_at` = VALUES(`generated_at`),
    `insight_version` = VALUES(`insight_version`),
    `update_time` = CURRENT_TIMESTAMP;

COMMIT;

-- 执行完成后可用以下语句检查结果：
-- SHOW TABLES LIKE 'tb_%insight%';
-- SHOW TABLES LIKE 'tb_topic';
-- SHOW TABLES LIKE 'tb_note_feature';
-- SELECT * FROM tb_topic ORDER BY sort, id;
-- SELECT note_id, type, title, hot_score FROM tb_note_feature ORDER BY hot_score DESC;
-- SELECT * FROM tb_note_topic_relation ORDER BY note_id, weight DESC;
-- SELECT * FROM tb_user_feature_snapshot;
-- SELECT * FROM tb_user_insight;
