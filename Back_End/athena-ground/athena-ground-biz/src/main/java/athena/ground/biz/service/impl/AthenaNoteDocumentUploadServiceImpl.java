package athena.ground.biz.service.impl;

import athena.athenaframework.utils.GlobalConstants;
import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.config.AthenaNoteDocumentRoutingProperties;
import athena.ground.biz.service.AthenaKnowledgeRouteService;
import athena.ground.biz.service.AthenaNoteDocumentUploadService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Athena note 标准 document 上传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaNoteDocumentUploadServiceImpl implements AthenaNoteDocumentUploadService {

    private static final String RAG_SUCCESS_CODE = "0";

    private final RestTemplate restTemplate;
    private final AthenaKnowledgeRouteService knowledgeRouteService;
    private final AthenaNoteDocumentRoutingProperties routingProperties;

    @Override
    public void upload(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        validateRequest(noteId, title, contentHtml, type, authorId);

        AthenaKnowledgeRouteService.KnowledgeTarget target = knowledgeRouteService.resolveTarget(Integer.valueOf(type));
        String fileName = buildFileName(noteId, title);
        String uploadUrl = routingProperties.getBaseUrl() + "/api/ragent/knowledge-base/" + target.kbId() + "/docs/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(GlobalConstants.USER_ID, String.valueOf(authorId));

        // 为文件设置 Content-Type
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_HTML);
        Resource fileResource = new NamedByteArrayResource(contentHtml.getBytes(StandardCharsets.UTF_8), fileName);
        HttpEntity<Resource> fileEntity = new HttpEntity<>(fileResource, fileHeaders);

        // 构建 metadata JSON
        String metadataJson = buildMetadataJson(noteId, title, type, authorId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileEntity);
        body.add("sourceType", "file");
        body.add("processMode", "pipeline");
        body.add("pipelineId", target.pipelineId());
        body.add("metadata", metadataJson);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        log.info("[AthenaNoteUpload] 开始上传 HTML 文档, noteId={}, authorId={}, type={}, kbCode={}, kbId={}, pipelineId={}, fileName={}, metadata={}",
                noteId, authorId, type, target.kbCode(), target.kbId(), target.pipelineId(), fileName, metadataJson);

        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 ragent 文档上传接口失败：返回为空");
        }

        String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
        String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 ragent 文档上传接口失败：" + message);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String docId = data == null || data.get("id") == null ? null : String.valueOf(data.get("id"));
        if (!StringUtils.hasText(docId)) {
            throw new RuntimeException("调用 ragent 文档上传接口失败：未返回 docId");
        }

        startChunk(docId, authorId);

        log.info("[AthenaNoteUpload] 上传并触发分块成功, noteId={}, authorId={}, kbCode={}, kbId={}, pipelineId={}, docId={}",
                noteId, authorId, target.kbCode(), target.kbId(), target.pipelineId(), docId);
    }

    @Override
    public void deleteByNoteId(Long noteId, Byte type, Long authorId) {
        if (noteId == null) {
            throw new IllegalArgumentException("笔记 ID 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("笔记类型不能为空");
        }
        if (authorId == null) {
            throw new IllegalArgumentException("作者 ID 不能为空");
        }

        AthenaKnowledgeRouteService.KnowledgeTarget target = knowledgeRouteService.resolveTarget(Integer.valueOf(type));
        List<String> docIds = listAthenaNoteDocIds(target.kbId(), noteId, authorId);
        if (docIds.isEmpty()) {
            log.info("[AthenaNoteUpload] 未找到待删除的 RAG 文档, noteId={}, kbId={}", noteId, target.kbId());
            return;
        }
        for (String docId : docIds) {
            deleteDocument(docId, authorId);
        }
        log.info("[AthenaNoteUpload] 删除 RAG 文档完成, noteId={}, kbId={}, docCount={}", noteId, target.kbId(), docIds.size());
    }

    private List<String> listAthenaNoteDocIds(String kbId, Long noteId, Long authorId) {
        String keyword = "athena-note-" + noteId;
        String pageUrl = routingProperties.getBaseUrl() + "/api/ragent/knowledge-base/" + kbId + "/docs?current=1&size=50&keyword=" + keyword;
        HttpHeaders headers = new HttpHeaders();
        headers.set(GlobalConstants.USER_ID, String.valueOf(authorId));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(pageUrl, HttpMethod.GET, requestEntity, String.class);
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 ragent 文档查询接口失败：返回为空");
        }
        String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
        String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 ragent 文档查询接口失败：" + message);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            return List.of();
        }
        Object recordsObj = data.get("records");
        if (!(recordsObj instanceof List<?> records)) {
            return List.of();
        }
        List<String> docIds = new ArrayList<>();
        String fileNamePrefix = keyword;
        for (Object record : records) {
            if (!(record instanceof Map<?, ?> map)) {
                continue;
            }
            Object docNameObj = map.get("docName");
            Object docIdObj = map.get("id");
            String docName = docNameObj == null ? null : String.valueOf(docNameObj);
            String docId = docIdObj == null ? null : String.valueOf(docIdObj);
            if (StringUtils.hasText(docId) && StringUtils.hasText(docName) && docName.startsWith(fileNamePrefix)) {
                docIds.add(docId);
            }
        }
        return docIds;
    }

    private void deleteDocument(String docId, Long authorId) {
        String deleteUrl = routingProperties.getBaseUrl() + "/api/ragent/knowledge-base/docs/" + docId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(GlobalConstants.USER_ID, String.valueOf(authorId));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, String.class);
        if (response.getStatusCode() == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("调用 ragent 文档删除接口失败：httpStatus=" + response.getStatusCode());
        }
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 ragent 文档删除接口失败：返回为空");
        }
        String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
        String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 ragent 文档删除接口失败：" + message);
        }
    }

    private void startChunk(String docId, Long authorId) {
        String chunkUrl = routingProperties.getBaseUrl() + "/api/ragent/knowledge-base/docs/" + docId + "/chunk";
        HttpHeaders headers = new HttpHeaders();
        headers.set(GlobalConstants.USER_ID, String.valueOf(authorId));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(chunkUrl, HttpMethod.POST, requestEntity, String.class);
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 ragent 文档分块接口失败：返回为空");
        }

        String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
        String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 ragent 文档分块接口失败：" + message);
        }
    }

    private void validateRequest(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        if (noteId == null) {
            throw new IllegalArgumentException("笔记 ID 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("笔记类型不能为空");
        }
        if (authorId == null) {
            throw new IllegalArgumentException("作者 ID 不能为空");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("笔记标题不能为空");
        }
        if (!StringUtils.hasText(contentHtml)) {
            throw new IllegalArgumentException("笔记内容不能为空");
        }
    }

    private String buildFileName(Long noteId, String title) {
        String normalizedTitle = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (!StringUtils.hasText(normalizedTitle)) {
            return "athena-note-" + noteId + ".html";
        }
        return "athena-note-" + noteId + "-" + normalizedTitle + ".html";
    }

    /**
     * 构建 metadata JSON 字符串
     */
    private String buildMetadataJson(Long noteId, String title, Byte type, Long authorId) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("noteId", noteId);
        metadata.put("title", title);
        metadata.put("type", type);
        metadata.put("authorId", authorId);
        metadata.put("source", "athena-note");
        try {
            return JsonUtils.toJsonString(metadata);
        } catch (Exception e) {
            log.error("构建 metadata JSON 失败", e);
            return "{}";
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
