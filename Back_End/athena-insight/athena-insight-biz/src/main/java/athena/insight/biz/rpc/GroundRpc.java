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
public class GroundRpc {

    @Resource
    private InsightGroundFeignApi insightGroundFeignApi;

    public List<Map<String, Object>> getBlogListPage(Integer pageNum, Integer pageSize) {
        Result<?> result = insightGroundFeignApi.getBlogListPage(pageNum, pageSize);
        return extractList(result, "查询公共内容列表失败, pageNum=" + pageNum + ", pageSize=" + pageSize);
    }

    public Map<String, Object> getBlogDetail(Long blogId, Byte type) {
        Result<?> result = insightGroundFeignApi.getBlogDetail(blogId, type);
        if (result == null || result.getCode() != 200 || !(result.getData() instanceof Map<?, ?> data)) {
            log.warn("[GroundRpc] 查询内容详情失败, blogId={}, type={}", blogId, type);
            return null;
        }
        return (Map<String, Object>) data;
    }

    public List<Map<String, Object>> getBlogListByUserId(Long userId, Integer pageNum, Integer pageSize) {
        Result<?> result = insightGroundFeignApi.getBlogListByUserId(userId, pageNum, pageSize);
        return extractList(result, "查询用户内容列表失败, userId=" + userId);
    }

    public List<Map<String, Object>> getBlogListByType(Integer type, Integer pageNum, Integer pageSize) {
        Result<?> result = insightGroundFeignApi.getBlogListByType(type, pageNum, pageSize);
        return extractList(result, "按类型查询公共内容失败, type=" + type);
    }

    public List<Map<String, Object>> likeList() {
        Result<?> result = insightGroundFeignApi.likeList();
        return extractList(result, "查询点赞列表失败");
    }

    public List<Map<String, Object>> collectList() {
        Result<?> result = insightGroundFeignApi.collectList();
        return extractList(result, "查询收藏列表失败");
    }

    public List<Map<String, Object>> viewHistory(Long cursor, Integer pageSize) {
        Result<?> result = insightGroundFeignApi.viewHistory(cursor, pageSize);
        return extractList(result, "查询浏览历史失败");
    }

    private List<Map<String, Object>> extractList(Result<?> result, String warnMessage) {
        if (result == null || result.getCode() != 200) {
            log.warn("[GroundRpc] {}", warnMessage);
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
