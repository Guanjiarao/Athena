package athena.ground.biz.service.impl;

import athena.athenaframework.utils.GlobalConstants;
import athena.athenaframework.utils.JsonUtils;
import athena.ground.biz.config.AthenaInsightProperties;
import athena.ground.biz.service.AthenaInsightNoteFeatureService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaInsightNoteFeatureServiceImpl implements AthenaInsightNoteFeatureService {

    private static final String INSIGHT_SUCCESS_CODE = "200";

    private final RestTemplate restTemplate;
    private final AthenaInsightProperties athenaInsightProperties;

    @Override
    public void deleteByNoteId(Long noteId, Long operatorUserId) {
        if (noteId == null || operatorUserId == null) {
            return;
        }
        String deleteUrl = athenaInsightProperties.getBaseUrl() + "/athena/insight/note-feature?noteId=" + noteId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(GlobalConstants.USER_ID, String.valueOf(operatorUserId));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, String.class);
        Map<String, Object> payload = JsonUtils.parseObject(response.getBody(), new TypeReference<Map<String, Object>>() {
        });
        if (payload == null) {
            throw new RuntimeException("调用 insight 内容特征删除接口失败：返回为空");
        }
        String code = payload.get("code") == null ? null : String.valueOf(payload.get("code"));
        String message = payload.get("message") == null ? null : String.valueOf(payload.get("message"));
        if (!Objects.equals(INSIGHT_SUCCESS_CODE, code)) {
            throw new RuntimeException("调用 insight 内容特征删除接口失败：" + message);
        }
        log.info("[AthenaInsightNoteFeatureService] 删除 insight 内容特征成功, noteId={}", noteId);
    }
}
