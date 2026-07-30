package athena.ground.biz.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 刷新 Athena note RAG 同步状态响应。
 */
@Data
public class NoteRagStatusRefreshResponse {

    private Integer scannedCount = 0;

    private Integer refreshedCount = 0;

    private Integer successCount = 0;

    private Integer failedCount = 0;

    private Integer missingCount = 0;

    private List<Item> items = new ArrayList<>();

    public void addScanned() {
        scannedCount++;
    }

    public void addRefreshed() {
        refreshedCount++;
    }

    public void addSuccess() {
        successCount++;
    }

    public void addFailed() {
        failedCount++;
    }

    public void addMissing() {
        missingCount++;
    }

    @Data
    public static class Item {

        private Long noteId;

        private String docId;

        private String syncStatus;

        private String ragStatus;

        private Integer chunkCount;

        private String action;

        private Boolean success;

        private String errorMessage;
    }
}
