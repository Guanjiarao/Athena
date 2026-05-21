

package com.nageoffer.ai.ragent.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * V3 流式问答完成时返回的引用信息。
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatReferencePayload(Long noteId, String title, String snippet, Float score) {
}
