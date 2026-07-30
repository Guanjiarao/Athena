package athena.athenaframework.mq.producer;

import org.apache.rocketmq.client.producer.SendResult;

/**
 * 消息队列生产者接口
 */
public interface MessageQueueProducer {

    /**
     * 发送普通消息
     *
     * @param topic   主题
     * @param keys    业务 key
     * @param bizDesc 业务描述
     * @param body    业务载荷
     * @return 发送结果
     */
    SendResult send(String topic, String keys, String bizDesc, Object body);
}
