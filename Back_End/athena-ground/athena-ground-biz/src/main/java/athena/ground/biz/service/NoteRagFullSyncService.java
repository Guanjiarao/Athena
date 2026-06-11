package athena.ground.biz.service;

import athena.ground.biz.domain.dto.NoteRagFullSyncRequest;
import athena.ground.biz.domain.dto.NoteRagFullSyncResponse;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshRequest;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshResponse;

/**
 * Athena note 全量同步到 RAG 服务。
 */
public interface NoteRagFullSyncService {

    NoteRagFullSyncResponse fullSync(NoteRagFullSyncRequest request);

    NoteRagStatusRefreshResponse refreshStatus(NoteRagStatusRefreshRequest request);
}
