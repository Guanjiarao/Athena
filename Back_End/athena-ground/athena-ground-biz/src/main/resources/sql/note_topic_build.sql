-- 笔记 topic 关系构建 SQL
-- 作用：为 ground 内部异步 topic 构建补齐 topic 与 note-topic 关系表

START TRANSACTION;

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

CREATE TABLE IF NOT EXISTS `tb_note_topic_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id` BIGINT NOT NULL COMMENT '内容ID',
    `topic_id` BIGINT NOT NULL COMMENT 'topic ID',
    `weight` DECIMAL(6,4) NOT NULL DEFAULT 1.0000 COMMENT 'topic权重',
    `source_type` TINYINT NOT NULL DEFAULT 2 COMMENT '来源类型：1人工 2规则 3系统',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_topic` (`note_id`, `topic_id`),
    KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容与topic关系表';

INSERT INTO `tb_topic` (`id`, `topic_code`, `topic_name`, `parent_id`, `topic_type`, `status`, `sort`, `description`)
VALUES
    (1, 'menstrual_care', '经期护理', NULL, 1, 1, 10, '围绕经期日常护理的内容主题'),
    (2, 'dysmenorrhea_relief', '痛经缓解', 1, 2, 1, 20, '围绕腹痛缓解、热敷与动作建议的健康主题'),
    (3, 'sleep_regulation', '睡眠调节', NULL, 1, 1, 30, '围绕睡眠质量改善的内容主题'),
    (4, 'emotion_management', '情绪调节', NULL, 1, 1, 40, '围绕情绪波动和压力管理的内容主题'),
    (5, 'diet_management', '饮食管理', NULL, 1, 1, 50, '围绕经期和女性健康饮食建议的内容主题')
ON DUPLICATE KEY UPDATE
    `topic_name` = VALUES(`topic_name`),
    `parent_id` = VALUES(`parent_id`),
    `topic_type` = VALUES(`topic_type`),
    `status` = VALUES(`status`),
    `sort` = VALUES(`sort`),
    `description` = VALUES(`description`);

COMMIT;
