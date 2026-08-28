package athena.cognition.biz.agenttask.mq;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * 认知图谱 Agent 任务 RocketMQ 消费者配置（照 athena-count 的
 * RocketMQConsumerConfig 先例：手工 DefaultMQPushConsumer +
 * MessageListenerConcurrently）。
 *
 * <p>worker 内部已把可重试失败管理成 FAILED/DEAD 终态，正常路径都返回
 * CONSUME_SUCCESS；只有消费者自身意外异常（如 DB 瞬断）才 RECONSUME_LATER
 * 交给 MQ 重投。
 */
@Slf4j
@Configuration
public class AgentTaskConsumerConfig {

    private static final String CONSUMER_GROUP = "athena-cognition-agent-task-consumer-group";

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Resource
    private AgentTaskConsumer agentTaskConsumer;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer agentTaskPushConsumer() throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(AgentTaskProducer.TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.info("[AgentTaskConsumer] 收到Agent任务消息, msgId={}, keys={}, body={}",
                        msg.getMsgId(), msg.getKeys(), body);
                try {
                    agentTaskConsumer.onMessage(body);
                } catch (Exception e) {
                    log.error("[AgentTaskConsumer] 消费Agent任务消息失败, msgId={}, keys={}",
                            msg.getMsgId(), msg.getKeys(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        log.info("[AgentTaskConsumer] 注册成功, group={}, topic={}", CONSUMER_GROUP, AgentTaskProducer.TOPIC);
        return consumer;
    }
}
