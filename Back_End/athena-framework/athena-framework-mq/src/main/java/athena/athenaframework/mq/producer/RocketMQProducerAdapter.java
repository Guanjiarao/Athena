package athena.athenaframework.mq.producer;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

/**
 * 基于 RocketMQ 的消息生产者实现
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMQProducerAdapter implements MessageQueueProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public SendResult send(String topic, String keys, String bizDesc, Object body) {
        String finalKeys = StringUtils.isBlank(keys) ? UUID.randomUUID().toString() : keys;
        String payload = JsonUtils.toJsonString(MessageWrapper.builder().keys(finalKeys).body(body).build());

        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader(MessageConst.PROPERTY_KEYS, finalKeys)
                .build();

        try {
            SendResult sendResult = rocketMQTemplate.syncSend(topic, message);
            log.info("[生产者] {} - 发送结果: {}, msgId={}, keys={}",
                    bizDesc, sendResult.getSendStatus(), sendResult.getMsgId(), finalKeys);
            return sendResult;
        } catch (Throwable ex) {
            log.error("[生产者] {} - 消息发送失败，topic={}, keys={}", bizDesc, topic, finalKeys, ex);
            throw ex;
        }
    }
}
