package athena.insight.biz.rpc;

import athena.athenaframework.result.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RecordFeignApi {

    @Resource
    private InsightRecordFeignApi insightRecordFeignApi;

    public Map<String, Object> getCycleStats(Long userId) {
        Result<?> result = insightRecordFeignApi.getCycleStats(userId);
        if (result == null || result.getCode() != 200 || !(result.getData() instanceof Map<?, ?> data)) {
            log.warn("[RecordFeignApi] 查询周期统计失败, userId={}", userId);
            return null;
        }
        return (Map<String, Object>) data;
    }

    public Map<String, Object> getPrediction(Long userId) {
        Result<?> result = insightRecordFeignApi.getPrediction(userId);
        if (result == null || result.getCode() != 200 || !(result.getData() instanceof Map<?, ?> data)) {
            log.warn("[RecordFeignApi] 查询周期预测失败, userId={}", userId);
            return null;
        }
        return (Map<String, Object>) data;
    }

    public List<Map<String, Object>> getRecords(Long userId, String startDate, String endDate) {
        Result<?> result = insightRecordFeignApi.getRecords(userId, startDate, endDate);
        if (result == null || result.getCode() != 200) {
            log.warn("[RecordFeignApi] 查询记录范围失败, userId={}, startDate={}, endDate={}", userId, startDate, endDate);
            return Collections.emptyList();
        }
        if (!(result.getData() instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }
}
