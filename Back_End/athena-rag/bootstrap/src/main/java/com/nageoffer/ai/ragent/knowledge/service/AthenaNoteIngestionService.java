

package com.nageoffer.ai.ragent.knowledge.service;

import com.nageoffer.ai.ragent.knowledge.service.dto.AthenaNoteSyncRequest;
import com.nageoffer.ai.ragent.knowledge.service.dto.AthenaNoteSyncResult;

/**
 * Athena 笔记摄取服务
 */
public interface AthenaNoteIngestionService {

    /**
     * 摄取 Athena 笔记到知识库
     *
     * @param request 同步请求
     * @return 同步结果
     */
    AthenaNoteSyncResult ingest(AthenaNoteSyncRequest request);
}
