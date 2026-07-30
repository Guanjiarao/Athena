

package com.nageoffer.ai.ragent.knowledge.controller.request;

import lombok.Data;

/**
 * 文档 metadata 查询请求。
 */
@Data
public class KnowledgeDocumentMetadataQueryRequest {

    /**
     * metadata.source，例如 athena-note。
     */
    private String source;

    /**
     * metadata.noteId。
     */
    private Long noteId;

    /**
     * metadata.type，可选。
     */
    private Integer type;

    /**
     * metadata.authorId，可选。
     */
    private Long authorId;

    /**
     * 限制返回数量，默认 20，最大 100。
     */
    private Integer limit;
}
