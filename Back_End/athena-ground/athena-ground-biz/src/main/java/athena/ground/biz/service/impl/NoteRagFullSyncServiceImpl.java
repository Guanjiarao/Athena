package athena.ground.biz.service.impl;

import athena.athenaframework.utils.GlobalConstants;
import athena.athenaframework.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import athena.ground.biz.config.AthenaNoteDocumentRoutingProperties;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteContentDO;
import athena.ground.biz.domain.dataobject.NoteRagSyncDO;
import athena.ground.biz.domain.dto.AthenaNoteDocumentUploadResult;
import athena.ground.biz.domain.dto.NoteRagFullSyncRequest;
import athena.ground.biz.domain.dto.NoteRagFullSyncResponse;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshRequest;
import athena.ground.biz.domain.dto.NoteRagStatusRefreshResponse;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteContentDOMapper;
import athena.ground.biz.domain.mapper.NoteRagSyncMapper;
import athena.ground.biz.service.AthenaKnowledgeRouteService;
import athena.ground.biz.service.AthenaNoteDocumentUploadService;
import athena.ground.biz.service.NoteRagFullSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Athena note 全量同步到 RAG 服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteRagFullSyncServiceImpl implements NoteRagFullSyncService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final NoteBasicDOMapper noteBasicMapper;
    private final NoteContentDOMapper noteContentDOMapper;
    private final NoteRagSyncMapper noteRagSyncMapper;
    private final AthenaKnowledgeRouteService knowledgeRouteService;
    private final AthenaNoteDocumentUploadService athenaNoteDocumentUploadService;
    private final AthenaNoteDocumentRoutingProperties routingProperties;
    private final RestTemplate restTemplate;

    @Override
    public NoteRagFullSyncResponse fullSync(NoteRagFullSyncRequest request) {
        NoteRagFullSyncRequest safeRequest = normalizeRequest(request);
        NoteRagFullSyncResponse response = new NoteRagFullSyncResponse();
        response.setDryRun(safeRequest.getDryRun());

        List<NoteBasicDO> candidates = noteBasicMapper.selectApprovedRagSyncCandidates(
                safeRequest.getNoteId(),
                safeRequest.getType(),
                safeRequest.getLimit()
        );

        for (NoteBasicDO note : candidates) {
            response.addScanned();
            NoteRagFullSyncResponse.Item item = buildBaseItem(note);
            response.getItems().add(item);

            NoteContentDO content = noteContentDOMapper.selectByNoteId(note.getNoteId());
            if (content == null || !StringUtils.hasText(content.getContent())) {
                upsertSyncRecord(note, null, null, "skipped", null,
                        "SKIP_EMPTY_CONTENT", "文章正文为空，跳过同步");
                markSkipped(response, item, "SKIP_EMPTY_CONTENT", "文章正文为空，跳过同步");
                continue;
            }

            AthenaKnowledgeRouteService.KnowledgeTarget target;
            try {
                target = knowledgeRouteService.resolveTarget(Integer.valueOf(note.getType()));
            } catch (Exception e) {
                upsertSyncRecord(note, content.getContent(), null, "failed", null,
                        "ROUTE_FAILED", e.getMessage());
                response.addFailed();
                item.setAction("ROUTE_FAILED");
                item.setReason("知识库路由失败");
                item.setExecuted(Boolean.FALSE);
                item.setSuccess(Boolean.FALSE);
                item.setErrorMessage(e.getMessage());
                log.error("[NoteRagFullSync] 知识库路由失败, noteId={}, title={}, type={}",
                        note.getNoteId(), note.getTitle(), note.getType(), e);
                continue;
            }

            String currentContentHash = AthenaNoteDocumentUploadServiceImpl.buildContentHash(
                    note.getTitle(), content.getContent(), note.getType(), note.getUserId());
            NoteRagSyncDO existingSync = noteRagSyncMapper.selectByNoteId(note.getNoteId());
            if (shouldSkipExistingSync(existingSync, currentContentHash)) {
                markSkipped(response, item, "SKIP_ALREADY_SYNCED",
                        "同步表已存在相同 contentHash 的有效 RAG doc，跳过重复上传");
                log.warn("[NoteRagFullSync] 跳过重复上传, noteId={}, docId={}, syncStatus={}, ragStatus={}, contentHash={}",
                        note.getNoteId(), existingSync.getDocId(), existingSync.getSyncStatus(), existingSync.getRagStatus(), existingSync.getContentHash());
                continue;
            }

            response.addCandidate();
            item.setAction("UPLOAD_FULL");
            item.setReason("审核通过且 type 需要进入 RAG");
            item.setExecuted(!safeRequest.getDryRun());

            if (safeRequest.getDryRun()) {
                upsertSyncRecord(note, content.getContent(), target, "pending", null,
                        "UPLOAD_FULL_DRY_RUN", null);
                item.setSuccess(null);
                log.info("[NoteRagFullSync] dry-run 计划上传, noteId={}, title={}, type={}, authorId={}",
                        note.getNoteId(), note.getTitle(), note.getType(), note.getUserId());
                continue;
            }

            upsertSyncRecord(note, content.getContent(), target, "uploading", null,
                    "UPLOAD_FULL", null);
            try {
                AthenaNoteDocumentUploadResult uploadResult = athenaNoteDocumentUploadService.upload(
                        note.getNoteId(),
                        note.getTitle(),
                        content.getContent(),
                        note.getType(),
                        note.getUserId()
                );
                upsertSyncRecord(note, uploadResult, "chunking", "UPLOAD_FULL", null);
                response.addUploaded();
                item.setSuccess(Boolean.TRUE);
                log.info("[NoteRagFullSync] 全量同步上传成功, noteId={}, title={}, type={}, authorId={}",
                        note.getNoteId(), note.getTitle(), note.getType(), note.getUserId());
                sleepQuietly(safeRequest.getSleepMs());
            } catch (Exception e) {
                upsertSyncRecord(note, content.getContent(), target, "failed", null,
                        "UPLOAD_FULL", e.getMessage());
                noteRagSyncMapper.increaseRetryCount(note.getNoteId());
                response.addFailed();
                item.setSuccess(Boolean.FALSE);
                item.setErrorMessage(e.getMessage());
                log.error("[NoteRagFullSync] 全量同步上传失败, noteId={}, title={}, type={}, authorId={}",
                        note.getNoteId(), note.getTitle(), note.getType(), note.getUserId(), e);
            }
        }

        return response;
    }

    @Override
    public NoteRagStatusRefreshResponse refreshStatus(NoteRagStatusRefreshRequest request) {
        NoteRagStatusRefreshRequest safeRequest = request == null ? new NoteRagStatusRefreshRequest() : request;
        Integer limit = safeRequest.getLimit();
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        limit = Math.min(limit, MAX_LIMIT);

        NoteRagStatusRefreshResponse response = new NoteRagStatusRefreshResponse();
        List<NoteRagSyncDO> candidates = noteRagSyncMapper.selectRefreshCandidates(safeRequest.getNoteId(), limit);
        for (NoteRagSyncDO sync : candidates) {
            response.addScanned();
            NoteRagStatusRefreshResponse.Item item = new NoteRagStatusRefreshResponse.Item();
            item.setNoteId(sync.getNoteId());
            item.setDocId(sync.getDocId());
            response.getItems().add(item);
            try {
                Map<String, Object> ragDoc = queryRagDocumentByMetadata(sync);
                if (ragDoc == null) {
                    response.addMissing();
                    item.setAction("REFRESH_MISSING");
                    item.setSuccess(Boolean.FALSE);
                    item.setErrorMessage("RAG 未查询到对应 document");
                    updateRefreshRecord(sync, null, "manual_check", null, 0, null,
                            "REFRESH_STATUS", "RAG 未查询到对应 document");
                    continue;
                }

                String docId = stringValue(ragDoc.get("id"));
                String ragStatus = stringValue(ragDoc.get("status"));
                Integer chunkCount = intValue(ragDoc.get("chunkCount"));
                Integer enabled = intValue(ragDoc.get("enabled"));
                String syncStatus = mapSyncStatus(ragStatus);

                updateRefreshRecord(sync, docId, syncStatus, ragStatus, chunkCount, enabled,
                        "REFRESH_STATUS", null);
                response.addRefreshed();
                if ("success".equals(syncStatus)) {
                    response.addSuccess();
                }
                if ("failed".equals(syncStatus)) {
                    response.addFailed();
                }
                item.setDocId(docId);
                item.setSyncStatus(syncStatus);
                item.setRagStatus(ragStatus);
                item.setChunkCount(chunkCount);
                item.setAction("REFRESH_STATUS");
                item.setSuccess(Boolean.TRUE);
            } catch (Exception e) {
                response.addFailed();
                item.setAction("REFRESH_STATUS");
                item.setSuccess(Boolean.FALSE);
                item.setErrorMessage(e.getMessage());
                log.error("[NoteRagFullSync] 刷新 RAG 同步状态失败, noteId={}, docId={}", sync.getNoteId(), sync.getDocId(), e);
            }
        }
        return response;
    }

    private Map<String, Object> queryRagDocumentByMetadata(NoteRagSyncDO sync) {
        String queryUrl = routingProperties.getBaseUrl() + "/api/ragent/knowledge-base/docs/metadata/query";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("source", "athena-note");
        requestBody.put("noteId", sync.getNoteId());
        requestBody.put("limit", 10);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sync.getAuthorId() != null) {
            headers.set(GlobalConstants.USER_ID, String.valueOf(sync.getAuthorId()));
        }
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(queryUrl, HttpMethod.POST, requestEntity, String.class);
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 RAG metadata 查询接口失败：返回为空");
        }
        String code = stringValue(payload.get("code"));
        String message = stringValue(payload.get("message"));
        if (!"0".equals(code)) {
            throw new RuntimeException("调用 RAG metadata 查询接口失败：" + message);
        }
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof List<?> docs) || docs.isEmpty()) {
            return null;
        }
        for (Object doc : docs) {
            if (doc instanceof Map<?, ?> docMap && sync.getDocId() != null && sync.getDocId().equals(stringValue(docMap.get("id")))) {
                return castStringObjectMap(docMap);
            }
        }
        Object first = docs.get(0);
        if (first instanceof Map<?, ?> docMap) {
            return castStringObjectMap(docMap);
        }
        return null;
    }

    private void updateRefreshRecord(NoteRagSyncDO sync, String docId, String syncStatus, String ragStatus,
                                     Integer chunkCount, Integer enabled, String lastAction, String lastError) {
        NoteRagSyncDO update = new NoteRagSyncDO();
        update.setNoteId(sync.getNoteId());
        update.setDocId(docId);
        update.setSyncStatus(syncStatus);
        update.setRagStatus(ragStatus);
        update.setChunkCount(chunkCount);
        update.setEnabled(enabled);
        update.setLastAction(lastAction);
        update.setLastError(lastError);
        update.setLastSyncTime(LocalDateTime.now());
        noteRagSyncMapper.updateByNoteIdSelective(update);
    }

    private String mapSyncStatus(String ragStatus) {
        if ("success".equals(ragStatus)) {
            return "success";
        }
        if ("failed".equals(ragStatus)) {
            return "failed";
        }
        if ("running".equals(ragStatus)) {
            return "chunking";
        }
        if ("pending".equals(ragStatus)) {
            return "uploaded";
        }
        return "manual_check";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean bool) {
            return Boolean.TRUE.equals(bool) ? 1 : 0;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if ("true".equalsIgnoreCase(text)) {
            return 1;
        }
        if ("false".equalsIgnoreCase(text)) {
            return 0;
        }
        return Integer.valueOf(text);
    }

    private NoteRagFullSyncRequest normalizeRequest(NoteRagFullSyncRequest request) {
        NoteRagFullSyncRequest safeRequest = request == null ? new NoteRagFullSyncRequest() : request;
        safeRequest.setDryRun(Boolean.TRUE.equals(safeRequest.getDryRun()));
        Integer limit = safeRequest.getLimit();
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        safeRequest.setLimit(Math.min(limit, MAX_LIMIT));
        Long sleepMs = safeRequest.getSleepMs();
        if (sleepMs == null || sleepMs < 0) {
            sleepMs = 0L;
        }
        safeRequest.setSleepMs(sleepMs);
        return safeRequest;
    }

    private NoteRagFullSyncResponse.Item buildBaseItem(NoteBasicDO note) {
        NoteRagFullSyncResponse.Item item = new NoteRagFullSyncResponse.Item();
        item.setNoteId(note.getNoteId());
        item.setTitle(note.getTitle());
        item.setType(note.getType());
        item.setAuthorId(note.getUserId());
        item.setExecuted(Boolean.FALSE);
        return item;
    }

    private void markSkipped(NoteRagFullSyncResponse response, NoteRagFullSyncResponse.Item item,
                             String action, String reason) {
        response.addSkipped();
        item.setAction(action);
        item.setReason(reason);
        item.setExecuted(Boolean.FALSE);
        item.setSuccess(null);
        log.warn("[NoteRagFullSync] 跳过同步, noteId={}, action={}, reason={}", item.getNoteId(), action, reason);
    }

    private boolean shouldSkipExistingSync(NoteRagSyncDO existingSync, String currentContentHash) {
        if (existingSync == null || !StringUtils.hasText(existingSync.getContentHash())) {
            return false;
        }
        if (!existingSync.getContentHash().equals(currentContentHash)) {
            return false;
        }
        String syncStatus = existingSync.getSyncStatus();
        if ("success".equals(syncStatus) || "chunking".equals(syncStatus)
                || "uploaded".equals(syncStatus) || "uploading".equals(syncStatus)) {
            return true;
        }
        return StringUtils.hasText(existingSync.getDocId())
                && ("running".equals(existingSync.getRagStatus()) || "success".equals(existingSync.getRagStatus())
                || "pending".equals(existingSync.getRagStatus()));
    }

    private void upsertSyncRecord(NoteBasicDO note, String contentHtml,
                                  AthenaKnowledgeRouteService.KnowledgeTarget target,
                                  String syncStatus, String ragStatus,
                                  String lastAction, String lastError) {
        NoteRagSyncDO record = buildBaseSyncRecord(note, syncStatus, ragStatus, lastAction, lastError);
        record.setKbId(target == null ? null : target.kbId());
        record.setKbCode(target == null ? null : target.kbCode());
        record.setDocName(buildFileName(note.getNoteId(), note.getTitle()));
        record.setSourceType("file");
        record.setProcessMode("pipeline");
        record.setPipelineId(target == null ? null : target.pipelineId());
        record.setContentHash(StringUtils.hasText(contentHtml)
                ? AthenaNoteDocumentUploadServiceImpl.buildContentHash(note.getTitle(), contentHtml, note.getType(), note.getUserId())
                : null);
        record.setMetadata(buildMetadataJson(note, record.getContentHash()));
        saveSyncRecord(record);
    }

    private void upsertSyncRecord(NoteBasicDO note, AthenaNoteDocumentUploadResult uploadResult,
                                  String syncStatus, String lastAction, String lastError) {
        NoteRagSyncDO record = buildBaseSyncRecord(note, syncStatus, uploadResult.getRagStatus(), lastAction, lastError);
        record.setKbId(uploadResult.getKbId());
        record.setKbCode(uploadResult.getKbCode());
        record.setDocId(uploadResult.getDocId());
        record.setDocName(uploadResult.getDocName());
        record.setSourceType(uploadResult.getSourceType());
        record.setProcessMode(uploadResult.getProcessMode());
        record.setPipelineId(uploadResult.getPipelineId());
        record.setMetadata(uploadResult.getMetadata());
        record.setContentHash(uploadResult.getContentHash());
        record.setSyncVersion(uploadResult.getSyncVersion());
        record.setChunkCount(uploadResult.getChunkCount());
        record.setEnabled(uploadResult.getEnabled());
        saveSyncRecord(record);
    }

    private NoteRagSyncDO buildBaseSyncRecord(NoteBasicDO note, String syncStatus, String ragStatus,
                                              String lastAction, String lastError) {
        NoteRagSyncDO record = new NoteRagSyncDO();
        record.setNoteId(note.getNoteId());
        record.setNoteType(note.getType());
        record.setAuthorId(note.getUserId());
        record.setTitle(note.getTitle());
        record.setSyncVersion(AthenaNoteDocumentUploadServiceImpl.SYNC_VERSION);
        record.setSyncStatus(syncStatus);
        record.setRagStatus(ragStatus);
        record.setChunkCount(0);
        record.setEnabled(1);
        record.setRetryCount(0);
        record.setLastAction(lastAction);
        record.setLastError(lastError);
        record.setLastSyncTime(LocalDateTime.now());
        return record;
    }

    private void saveSyncRecord(NoteRagSyncDO record) {
        if (noteRagSyncMapper.selectByNoteId(record.getNoteId()) == null) {
            noteRagSyncMapper.insert(record);
        } else {
            noteRagSyncMapper.updateByNoteIdSelective(record);
        }
    }

    private String buildMetadataJson(NoteBasicDO note, String contentHash) {
        if (!StringUtils.hasText(contentHash)) {
            return null;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("noteId", note.getNoteId());
        metadata.put("title", note.getTitle());
        metadata.put("type", note.getType());
        metadata.put("authorId", note.getUserId());
        metadata.put("source", "athena-note");
        metadata.put("contentHash", contentHash);
        metadata.put("syncVersion", AthenaNoteDocumentUploadServiceImpl.SYNC_VERSION);
        return JsonUtils.toJsonString(metadata);
    }

    private String buildFileName(Long noteId, String title) {
        String normalizedTitle = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (!StringUtils.hasText(normalizedTitle)) {
            return "athena-note-" + noteId + ".html";
        }
        return "athena-note-" + noteId + "-" + normalizedTitle + ".html";
    }

    private void sleepQuietly(Long sleepMs) {
        if (sleepMs == null || sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[NoteRagFullSync] 同步等待被中断", e);
        }
    }
}
