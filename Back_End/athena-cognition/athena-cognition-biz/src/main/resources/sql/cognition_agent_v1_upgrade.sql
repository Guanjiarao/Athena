-- Athena Cognition Agent V1 upgrade：对既有 cognition_evidence 表的增量变更。
-- 核查结论（对照 cognition_v1.sql 第 76-91 行的实际结构）：
--   * 现有列：id, user_id, source_type VARCHAR(32), source_id VARCHAR(128),
--     fact_level, summary, occurred_at, linked_at, active, deleted, created_at。
--   * 现有索引 idx_cognition_evidence_user_source (user_id, source_type, source_id)
--     是【非唯一】普通索引，没有等价的唯一索引，因此需要新增唯一索引。
--   * 键长估算：BIGINT(8) + VARCHAR(32)*utf8mb4(约 130) + VARCHAR(128)*utf8mb4(约 514)
--     合计约 652 字节，远低于 InnoDB 3072 字节上限，可以直接建唯一索引。
-- 前置条件：表中存量数据在 (user_id, source_type, source_id) 上必须无重复，
--   否则唯一索引会创建失败；如有重复需先人工清理。可用以下语句预检：
--   SELECT user_id, source_type, source_id, COUNT(*) c FROM cognition_evidence
--   GROUP BY user_id, source_type, source_id HAVING c > 1;
-- 说明：旧的非唯一索引 idx_cognition_evidence_user_source 在新唯一索引建成后
--   功能上冗余，但为遵守「不改动现有对象」原则予以保留，不影响正确性。

-- 1. 内容指纹：用于证据去重（同一来源内容未变化时跳过重复处理），可空以兼容存量行
ALTER TABLE `cognition_evidence`
    ADD COLUMN `content_fingerprint` VARCHAR(128) NULL COMMENT '来源内容指纹（哈希），用于证据去重与变更检测' AFTER `summary`;

-- 2. 同一用户的同一来源（source_type + source_id）只允许一条证据
ALTER TABLE `cognition_evidence`
    ADD UNIQUE KEY `uk_cognition_evidence_user_source` (`user_id`, `source_type`, `source_id`);
