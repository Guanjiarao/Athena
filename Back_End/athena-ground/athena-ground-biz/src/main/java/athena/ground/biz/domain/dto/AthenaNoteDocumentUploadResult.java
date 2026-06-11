package athena.ground.biz.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Athena note 上传为 RAG document 的结果。
 */
@Data
@Builder
public class AthenaNoteDocumentUploadResult {

    private Long noteId;

    private String kbId;

    private String kbCode;

    private String pipelineId;

    private String docId;

    private String docName;

    private String sourceType;

    private String processMode;

    private String metadata;

    private String contentHash;

    private Integer syncVersion;

    private String ragStatus;

    private Integer chunkCount;

    private Integer enabled;
}
