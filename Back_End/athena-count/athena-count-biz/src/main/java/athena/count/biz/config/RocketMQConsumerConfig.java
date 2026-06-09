package athena.count.biz.config;

import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.consumer.CountDeltaConsumer;
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
 * 计数服务 RocketMQ 消费者配置。
 *
 * <p>各业务模块直接向统一计数 topic 发送 CounterDeltaDTO，计数服务负责异步聚合。</p>
 */
@Slf4j
@Configuration
public class RocketMQConsumerConfig {

    private static final String COUNT_DELTA_CONSUMER_GROUP = "athena-count-delta-consumer-group";

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Resource
    private CountDeltaConsumer countDeltaConsumer;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer countDeltaPushConsumer() throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(COUNT_DELTA_CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(CountConstants.EVENT_TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.info("[CountDeltaRocketMQConsumer] 收到计数消息, msgId={}, keys={}, body={}",
                        msg.getMsgId(), msg.getKeys(), body);
                try {
                    countDeltaConsumer.onMessage(body);
                } catch (Exception e) {
                    log.error("[CountDeltaRocketMQConsumer] 消费计数消息失败, msgId={}, keys={}",
                            msg.getMsgId(), msg.getKeys(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        log.info("[CountDeltaRocketMQConsumer] 注册成功, group={}, topic={}",
                COUNT_DELTA_CONSUMER_GROUP, CountConstants.EVENT_TOPIC);
        return consumer;
    }
}
