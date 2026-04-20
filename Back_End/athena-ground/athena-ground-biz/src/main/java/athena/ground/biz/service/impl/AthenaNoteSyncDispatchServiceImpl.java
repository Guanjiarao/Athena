package athena.ground.biz.service.impl;

import athena.ground.biz.mq.event.AthenaNoteSyncEvent;
import athena.ground.biz.mq.producer.AthenaNoteSyncProducer;
import athena.ground.biz.service.AthenaNoteSyncDispatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Athena 笔记知识同步派发服务实现
 */
@Slf4j
@Service
public class AthenaNoteSyncDispatchServiceImpl implements AthenaNoteSyncDispatchService {

    @Resource
    private AthenaNoteSyncProducer athenaNoteSyncProducer;

    @Override
    public void dispatch(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        athenaNoteSyncProducer.send(AthenaNoteSyncEvent.builder()
                .noteId(noteId)
                .title(title)
                .contentHtml(contentHtml)
                .type(type == null ? null : Integer.valueOf(type))
                .authorId(authorId)
                .build());
        log.info("[AthenaNoteSyncDispatchService] 已派发知识同步消息, noteId={}, type={}", noteId, type);
    }
}
