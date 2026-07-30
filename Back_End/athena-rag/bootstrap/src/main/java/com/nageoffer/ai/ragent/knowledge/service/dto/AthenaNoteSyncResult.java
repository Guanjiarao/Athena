

package com.nageoffer.ai.ragent.knowledge.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Athena 笔记同步结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthenaNoteSyncResult {

    private Long noteId;

    private String kbCode;

    private String collectionName;

    private Integer chunkCount;
}
