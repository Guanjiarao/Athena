package athena.count.biz.mq.producer;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.biz.constant.CountConstants;
import athena.count.biz.mq.event.CountDeltaEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CountDeltaProducer {

    @Resource
    private MessageQueueProducer messageQueueProducer;

    public void send(CounterDeltaDTO deltaDTO) {
        CountDeltaEvent event = new CountDeltaEvent();
        event.setEventId(deltaDTO.getEventId());
        event.setScope(deltaDTO.getScope());
        event.setTargetId(deltaDTO.getTargetId());
        event.setCounterType(deltaDTO.getCounterType());
        event.setDelta(deltaDTO.getDelta());
        event.setTimestamp(System.currentTimeMillis());
        messageQueueProducer.send(CountConstants.EVENT_TOPIC, event.getEventId(), CountConstants.EVENT_BIZ_DESC, event);
        log.info("[CountDeltaProducer] 发送计数事件成功, eventId={}, scope={}, targetId={}, counterType={}, delta={}",
                event.getEventId(), event.getScope(), event.getTargetId(), event.getCounterType(), event.getDelta());
    }
}
