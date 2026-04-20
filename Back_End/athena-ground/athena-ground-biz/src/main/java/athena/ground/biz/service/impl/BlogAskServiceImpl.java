package athena.ground.biz.service.impl;

import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.domain.dto.BlogAskDTO;
import athena.ground.biz.domain.dto.BlogAskReferenceDTO;
import athena.ground.biz.domain.dto.BlogAskResultDTO;
import athena.ground.biz.service.BlogAskService;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 博客问答服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogAskServiceImpl implements BlogAskService {

    private static final String RAG_ASK_URL = "http://localhost:9090/api/ragent/athena/rag/ask";
    private static final String RAG_SUCCESS_CODE = "0";

    private final RestTemplate restTemplate;

    @Override
    public BlogAskResultDTO ask(BlogAskDTO request) {
        validateRequest(request);

        log.info("[BlogAsk] 开始调用 ragent 问答接口, age={}, question={}", request.getAge(), abbreviateQuestion(request.getQuestion()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BlogAskDTO> httpEntity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                RAG_ASK_URL,
                HttpMethod.POST,
                httpEntity,
                String.class
        );
        log.info("[BlogAsk] ragent 响应完成, httpStatus={}, bodyLength={}",
                response.getStatusCode().value(), response.getBody() == null ? 0 : response.getBody().length());

        Map<String, Object> payload = JsonUtils.parseObject(
                response.getBody(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        if (payload == null) {
            throw new RuntimeException("调用 ragent 问答接口失败：返回为空");
        }

        String code = castString(payload.get("code"));
        String message = castString(payload.get("message"));
        log.info("[BlogAsk] ragent 返回解析完成, code={}, message={}", code, message);
        if (!Objects.equals(RAG_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 ragent 问答接口失败：" + message);
        }

        Object data = payload.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new RuntimeException("调用 ragent 问答接口失败：返回数据格式不正确");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dataObject = (Map<String, Object>) dataMap;
        BlogAskResultDTO result = convertResult(dataObject);
        log.info("[BlogAsk] 问答完成, resolvedAge={}, kbCodes={}, referenceCount={}, answerLength={}",
                result.getResolvedAge(), result.getKbCodes(),
                result.getReferences() == null ? 0 : result.getReferences().size(),
                result.getAnswer() == null ? 0 : result.getAnswer().length());
        return result;
    }

    private void validateRequest(BlogAskDTO request) {
        if (request == null || StrUtil.isBlank(request.getQuestion())) {
            throw new RuntimeException("问题不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private BlogAskResultDTO convertResult(Map<String, Object> data) {
        List<Map<String, Object>> references = (List<Map<String, Object>>) data.get("references");
        return BlogAskResultDTO.builder()
                .answer(castString(data.get("answer")))
                .resolvedAge(castInteger(data.get("resolvedAge")))
                .kbCodes((List<String>) data.get("kbCodes"))
                .references(references == null ? List.of() : references.stream()
                        .map(this::convertReference)
                        .toList())
                .build();
    }

    private BlogAskReferenceDTO convertReference(Map<String, Object> item) {
        return BlogAskReferenceDTO.builder()
                .noteId(castLong(item.get("noteId")))
                .title(castString(item.get("title")))
                .snippet(castString(item.get("snippet")))
                .score(castFloat(item.get("score")))
                .build();
    }

    private String castString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer castInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long castLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Float castFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : null;
    }

    private String abbreviateQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }
        String normalized = question.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60) + "...";
    }
}
