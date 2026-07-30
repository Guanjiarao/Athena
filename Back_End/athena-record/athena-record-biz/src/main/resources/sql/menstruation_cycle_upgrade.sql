-- 经期周期表模型升级脚本
-- 适用于已执行旧版 menstruation_cycle.sql 的数据库。
-- 升级目标：移除“未结束经期”模型假设，改为新增时即落库完整日期范围。

-- 1. 修复旧数据：旧模型中 end_date 为空表示未确认结束；新模型中 end_date 必须有值。
UPDATE `menstruation_cycle`
SET `end_date` = DATE_ADD(`start_date`, INTERVAL 4 DAY)
WHERE `end_date` IS NULL;

-- 2. 修复旧数据：持续天数按闭区间计算。
UPDATE `menstruation_cycle`
SET `duration_days` = DATEDIFF(`end_date`, `start_date`) + 1
WHERE `duration_days` IS NULL;

-- 3. 调整字段约束和注释，使表结构与当前代码模型一致。
ALTER TABLE `menstruation_cycle`
  MODIFY COLUMN `end_date` date NOT NULL COMMENT '经期结束日期，新增时未传则默认开始日期后第5天',
  MODIFY COLUMN `duration_days` int NOT NULL COMMENT '经期持续天数，按闭区间计算',
  MODIFY COLUMN `cycle_length` int DEFAULT NULL COMMENT '与上一次实际经期开始日期的间隔天数，第一条实际经期为空';

-- 4. 增加月视图范围查询索引。
ALTER TABLE `menstruation_cycle`
  ADD KEY `idx_user_predict_range` (`user_id`, `is_predicted`, `start_date`, `end_date`);
