CREATE TABLE `menstruation_cycle` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `start_date` date NOT NULL COMMENT '经期开始日期',
  `end_date` date DEFAULT NULL COMMENT '经期真实结束日期，为空表示尚未确认结束',
  `duration_days` int DEFAULT NULL COMMENT '经期持续天数，按闭区间计算',
  `cycle_length` int DEFAULT NULL COMMENT '与上一次实际经期开始日期的间隔天数',
  `is_predicted` tinyint NOT NULL DEFAULT '0' COMMENT '0-实际数据，1-预测数据',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_start_predict` (`user_id`, `start_date`, `is_predicted`),
  KEY `idx_user_start_date` (`user_id`, `start_date`),
  KEY `idx_user_predict_start` (`user_id`, `is_predicted`, `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经期周期表';
