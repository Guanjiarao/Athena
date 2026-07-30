package athena.ground.biz.controller;

import athena.athenaframework.result.Result;
import athena.ground.biz.domain.dto.NoteRagFullSyncRequest;
import athena.ground.biz.domain.dto.NoteRagFullSyncResponse;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshRequest;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshResponse;
import athena.ground.biz.service.NoteRagFullSyncService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Athena note 同步 RAG 管理接口。
 */
@RestController
@RequestMapping("/athena/admin/rag-sync/notes")
public class AdminNoteRagSyncController {

    @Resource
    private NoteRagFullSyncService noteRagFullSyncService;

    /**
     * 全量同步审核通过且需要进入 RAG 的 note。
     */
    @PostMapping("/full")
    public Result<NoteRagFullSyncResponse> fullSync(@RequestBody(required = false) NoteRagFullSyncRequest request) {
        return Result.ok(noteRagFullSyncService.fullSync(request));
    }

    /**
     * 刷新已上传 note 的 RAG 异步处理状态。
     */
    @PostMapping("/refresh-status")
    public Result<NoteRagStatusRefreshResponse> refreshStatus(@RequestBody(required = false) NoteRagStatusRefreshRequest request) {
        return Result.ok(noteRagFullSyncService.refreshStatus(request));
    }
}
