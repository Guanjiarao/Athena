package athena.cognition.biz.agenttask.mq;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dispatches a persisted agent task to RocketMQ (照 athena-count 的
 * CountDeltaProducer 先例）。任务行先落库，消息只带 taskId + triggerType，
 * keys=taskId 便于 broker 侧按业务键追踪/排查。
 */
@Slf4j
@Component
public class AgentTaskProducer {

    public static final String TOPIC = "ATHENA_COGNITION_AGENT_TASK";
    private static final String BIZ_DESC = "认知图谱Agent任务";

    private final MessageQueueProducer messageQueueProducer;

    public AgentTaskProducer(MessageQueueProducer messageQueueProducer) {
        this.messageQueueProducer = messageQueueProducer;
    }

    public void send(String taskId, String triggerType) {
        messageQueueProducer.send(TOPIC, taskId, BIZ_DESC, new AgentTaskMessage(taskId, triggerType));
        log.info("[AgentTaskProducer] 发送Agent任务消息成功, taskId={}, triggerType={}", taskId, triggerType);
    }
}
