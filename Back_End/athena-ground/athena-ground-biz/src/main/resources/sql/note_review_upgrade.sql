-- 审核改造 SQL 脚本
-- 作用：为 tb_note_basic 补齐审核字段，并将旧的 tb_note.status 回填到 tb_note_basic
-- 执行前建议先备份相关表

START TRANSACTION;

ALTER TABLE tb_note_basic
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1审核通过 2审核拒绝' AFTER `type`,
    ADD COLUMN review_remark VARCHAR(255) NULL COMMENT '审核备注/拒绝原因' AFTER status,
    ADD COLUMN review_time DATETIME NULL COMMENT '审核时间' AFTER review_remark,
    ADD COLUMN reviewer_id BIGINT NULL COMMENT '审核人ID' AFTER review_time;

-- 将历史数据从 tb_note.status 回填到 tb_note_basic.status
-- 规则：
-- 1. tb_note.status 有值时，按旧值回填
-- 2. tb_note.status 为空时，默认视为已通过（1），避免历史已发布内容被全部隐藏
UPDATE tb_note_basic nb
LEFT JOIN tb_note n ON n.id = nb.note_id
SET nb.status = CASE
                    WHEN n.status IS NULL THEN 1
                    ELSE n.status
                END
WHERE nb.status = 0;

COMMIT;

-- 执行后可用以下语句检查结果：
-- SELECT status, COUNT(*) FROM tb_note_basic GROUP BY status;
-- SELECT note_id, title, status, review_remark, review_time, reviewer_id FROM tb_note_basic ORDER BY note_id DESC LIMIT 20;
