-- Athena Cognition Agent V2 upgrade：Agent 任务改由 RocketMQ 驱动后的增量变更。
-- 背景：任务提交从内存线程池改为「落库 + MQ 消息」。MQ 消息只携带 taskId/triggerType，
--   执行上下文（clueId、clueIds、feedback 结果等）必须在任务落库时持久化，
--   否则崩溃恢复清扫器在消息丢失时无法重建上下文重新投递。
-- 前置条件：无（新增可空列，对存量行无影响）。

-- 1. 任务执行上下文快照：创建任务时写入，消费者/清扫器按 triggerType 反序列化重建上下文
ALTER TABLE `cognition_agent_task`
    ADD COLUMN `payload_json` JSON NULL COMMENT '任务执行上下文快照（MQ 消费者/崩溃恢复清扫器据此重建执行上下文）' AFTER `error_retryable`;
