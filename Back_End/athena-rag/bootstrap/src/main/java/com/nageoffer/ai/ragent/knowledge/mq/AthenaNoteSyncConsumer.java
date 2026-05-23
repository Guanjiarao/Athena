

package com.nageoffer.ai.ragent.knowledge.mq;

import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.knowledge.mq.event.AthenaNoteSyncEvent;
import com.nageoffer.ai.ragent.knowledge.service.AthenaNoteIngestionService;
import com.nageoffer.ai.ragent.knowledge.service.dto.AthenaNoteSyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * Athena 笔记同步 MQ 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "note-knowledge-sync",
        consumerGroup = "note-knowledge-sync_cg"
)
public class AthenaNoteSyncConsumer implements RocketMQListener<MessageWrapper<AthenaNoteSyncEvent>> {

    private final AthenaNoteIngestionService athenaNoteIngestionService;

    @Override
    public void onMessage(MessageWrapper<AthenaNoteSyncEvent> message) {
        AthenaNoteSyncEvent event = message.getBody();

        log.info("[AthenaNoteSyncConsumer] ===== 收到 Athena 笔记同步事件 ===== noteId={}, title={}, type={}, authorId={}, keys={}",
                event.getNoteId(), event.getTitle(), event.getType(), event.getAuthorId(), message.getKeys());

        athenaNoteIngestionService.ingest(AthenaNoteSyncRequest.builder()
                .noteId(event.getNoteId())
                .title(event.getTitle())
                .contentHtml(event.getContentHtml())
                .type(event.getType())
                .authorId(event.getAuthorId())
                .build());

        log.info("[AthenaNoteSyncConsumer] ===== Athena 笔记同步完成 ===== noteId={}", event.getNoteId());
    }
}
