/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

        log.info("[消费者] 开始处理 Athena 笔记同步事件，noteId={}, type={}, keys={}",
                event.getNoteId(), event.getType(), message.getKeys());

        athenaNoteIngestionService.ingest(AthenaNoteSyncRequest.builder()
                .noteId(event.getNoteId())
                .title(event.getTitle())
                .contentHtml(event.getContentHtml())
                .type(event.getType())
                .authorId(event.getAuthorId())
                .build());
    }
}
