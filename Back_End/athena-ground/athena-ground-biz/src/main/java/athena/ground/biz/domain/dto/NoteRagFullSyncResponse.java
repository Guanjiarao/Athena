package athena.ground.biz.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Athena note 全量同步到 RAG 的执行结果。
 */
@Data
public class NoteRagFullSyncResponse {

    private Boolean dryRun;

    private Integer scannedCount = 0;

    private Integer candidateCount = 0;

    private Integer uploadedCount = 0;

    private Integer skippedCount = 0;

    private Integer failedCount = 0;

    private List<Item> items = new ArrayList<>();

    public void addScanned() {
        scannedCount++;
    }

    public void addCandidate() {
        candidateCount++;
    }

    public void addUploaded() {
        uploadedCount++;
    }

    public void addSkipped() {
        skippedCount++;
    }

    public void addFailed() {
        failedCount++;
    }

    @Data
    public static class Item {

        private Long noteId;

        private String title;

        private Byte type;

        private Long authorId;

        private String action;

        private String reason;

        private Boolean executed;

        private Boolean success;

        private String errorMessage;
    }
}
