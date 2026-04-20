package athena.ground.biz.config;

import athena.ground.biz.constant.NoteInteractionConstants;
import athena.ground.biz.constant.NoteTopicBuildConstants;
import athena.ground.biz.constant.ViewRecordConstants;
import athena.ground.biz.mq.consumer.NoteInteractionConsumer;
import athena.ground.biz.mq.consumer.NoteTopicBuildConsumer;
import athena.ground.biz.mq.consumer.ViewRecordConsumer;
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
 * RocketMQ 消费者手动配置
 *
 * <p>保留手动注册消费者的方式，避免当前环境下 listener 注解体系兼容性不稳定。</p>
 */
@Slf4j
@Configuration
public class RocketMQConsumerConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Resource
    private ViewRecordConsumer viewRecordConsumer;

    @Resource
    private NoteInteractionConsumer noteInteractionConsumer;

    @Resource
    private NoteTopicBuildConsumer noteTopicBuildConsumer;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer viewRecordPushConsumer() throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(ViewRecordConstants.VIEW_RECORD_CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(ViewRecordConstants.VIEW_RECORD_TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.info("[RocketMQ Consumer] 收到消息, msgId={}, keys={}, body={}", msg.getMsgId(), msg.getKeys(), body);
                try {
                    viewRecordConsumer.onMessage(body);
                } catch (Exception e) {
                    log.error("[RocketMQ Consumer] 消费失败, msgId={}, keys={}", msg.getMsgId(), msg.getKeys(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        log.info("[RocketMQ Consumer] 注册成功, group={}, topic={}",
                ViewRecordConstants.VIEW_RECORD_CONSUMER_GROUP,
                ViewRecordConstants.VIEW_RECORD_TOPIC);
        return consumer;
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer noteInteractionPushConsumer() throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(NoteInteractionConstants.NOTE_INTERACTION_CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(NoteInteractionConstants.NOTE_INTERACTION_TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.info("[RocketMQ Interaction Consumer] 收到消息, msgId={}, keys={}, body={}", msg.getMsgId(), msg.getKeys(), body);
                try {
                    noteInteractionConsumer.onMessage(body);
                } catch (Exception e) {
                    log.error("[RocketMQ Interaction Consumer] 消费失败, msgId={}, keys={}", msg.getMsgId(), msg.getKeys(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        log.info("[RocketMQ Interaction Consumer] 注册成功, group={}, topic={}",
                NoteInteractionConstants.NOTE_INTERACTION_CONSUMER_GROUP,
                NoteInteractionConstants.NOTE_INTERACTION_TOPIC);
        return consumer;
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer noteTopicBuildPushConsumer() throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(NoteTopicBuildConstants.NOTE_TOPIC_BUILD_CONSUMER_GROUP);
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(NoteTopicBuildConstants.NOTE_TOPIC_BUILD_TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.info("[RocketMQ TopicBuild Consumer] 收到消息, msgId={}, keys={}, body={}", msg.getMsgId(), msg.getKeys(), body);
                try {
                    noteTopicBuildConsumer.onMessage(body);
                } catch (Exception e) {
                    log.error("[RocketMQ TopicBuild Consumer] 消费失败, msgId={}, keys={}", msg.getMsgId(), msg.getKeys(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        log.info("[RocketMQ TopicBuild Consumer] 注册成功, group={}, topic={}",
                NoteTopicBuildConstants.NOTE_TOPIC_BUILD_CONSUMER_GROUP,
                NoteTopicBuildConstants.NOTE_TOPIC_BUILD_TOPIC);
        return consumer;
    }
}
