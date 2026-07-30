package athena.ground.biz.domain.dto;

import lombok.Data;

/**
 * 刷新 Athena note RAG 同步状态请求。
 */
@Data
public class NoteRagStatusRefreshRequest {

    /**
     * 可选：只刷新单篇 note。
     */
    private Long noteId;

    /**
     * 最多刷新数量。
     */
    private Integer limit = 100;
}
