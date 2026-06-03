UPDATE `menstruation_cycle`
SET
  `end_date` = DATE_ADD(`start_date`, INTERVAL 4 DAY),
  `duration_days` = 5
WHERE `end_date` IS NULL;

UPDATE `menstruation_cycle`
SET `duration_days` = DATEDIFF(`end_date`, `start_date`) + 1
WHERE `duration_days` IS NULL
  AND `end_date` IS NOT NULL;

ALTER TABLE `menstruation_cycle`
  MODIFY COLUMN `end_date` date NOT NULL COMMENT '经期结束日期，新增时未传则默认开始日期后第5天',
  MODIFY COLUMN `duration_days` int NOT NULL COMMENT '经期持续天数，按闭区间计算',
  MODIFY COLUMN `cycle_length` int DEFAULT NULL COMMENT '与上一次实际经期开始日期的间隔天数，第一条实际经期为空';

ALTER TABLE `menstruation_cycle`
  DROP INDEX `idx_user_start_date`,
  ADD INDEX `idx_user_predict_range` (`user_id`, `is_predicted`, `start_date`, `end_date`);
