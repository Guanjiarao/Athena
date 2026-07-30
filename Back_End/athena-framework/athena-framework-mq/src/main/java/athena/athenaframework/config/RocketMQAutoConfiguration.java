package athena.athenaframework.config;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.athenaframework.mq.producer.RocketMQProducerAdapter;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 自动配置
 */
@AutoConfiguration
public class RocketMQAutoConfiguration {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.producer.send-message-timeout:10000}")
    private int sendMessageTimeout;

    @Value("${rocketmq.producer.vip-channel-enabled:false}")
    private boolean vipChannelEnabled;

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer defaultMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendMessageTimeout);
        producer.setVipChannelEnabled(vipChannelEnabled);
        return producer;
    }

    @Bean
    public RocketMQTemplate rocketMQTemplate(DefaultMQProducer defaultMQProducer) {
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(defaultMQProducer);
        return template;
    }

    @Bean
    public MessageQueueProducer messageQueueProducer(RocketMQTemplate rocketMQTemplate) {
        return new RocketMQProducerAdapter(rocketMQTemplate);
    }
}
