package athena.ground.biz.domain.dto;

import lombok.Data;

/**
 * Athena note 全量同步到 RAG 的请求参数。
 */
@Data
public class NoteRagFullSyncRequest {

    /**
     * 是否只预览，不真实上传。
     */
    private Boolean dryRun = Boolean.TRUE;

    /**
     * 可选：只同步单篇 note。
     */
    private Long noteId;

    /**
     * 可选：只同步指定类型。
     */
    private Byte type;

    /**
     * 最多扫描数量。
     */
    private Integer limit = 100;

    /**
     * 每篇上传后的等待时间，避免打爆 RAG。
     */
    private Long sleepMs = 300L;
}
