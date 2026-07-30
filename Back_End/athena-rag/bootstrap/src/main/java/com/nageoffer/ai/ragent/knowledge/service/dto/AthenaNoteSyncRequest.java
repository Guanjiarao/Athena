

package com.nageoffer.ai.ragent.knowledge.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Athena 笔记同步请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthenaNoteSyncRequest {

    private Long noteId;

    private String title;

    private String contentHtml;

    private Integer type;

    private Long authorId;
}
