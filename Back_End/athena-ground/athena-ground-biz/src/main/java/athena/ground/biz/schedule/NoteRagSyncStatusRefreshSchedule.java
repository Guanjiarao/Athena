package athena.ground.biz.schedule;

import athena.ground.biz.domain.dto.NoteRagStatusRefreshRequest;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshResponse;
import athena.ground.biz.service.NoteRagFullSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Athena note RAG 同步状态定时刷新任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteRagSyncStatusRefreshSchedule {

    private static final int DEFAULT_REFRESH_LIMIT = 100;

    private final NoteRagFullSyncService noteRagFullSyncService;

    /**
     * 每天凌晨 3 点刷新 RAG 异步处理结果。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshStatusDaily() {
        NoteRagStatusRefreshRequest request = new NoteRagStatusRefreshRequest();
        request.setLimit(DEFAULT_REFRESH_LIMIT);
        try {
            NoteRagStatusRefreshResponse response = noteRagFullSyncService.refreshStatus(request);
            log.info("[NoteRagSyncStatusRefreshSchedule] 定时刷新完成, scanned={}, refreshed={}, success={}, failed={}, missing={}",
                    response.getScannedCount(), response.getRefreshedCount(), response.getSuccessCount(),
                    response.getFailedCount(), response.getMissingCount());
        } catch (Exception e) {
            log.error("[NoteRagSyncStatusRefreshSchedule] 定时刷新失败", e);
        }
    }
}
