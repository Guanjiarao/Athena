package athena.insight.biz.service.impl;

import athena.insight.biz.domain.dto.ReportQueryDTO;
import athena.insight.biz.domain.dto.RecommendQueryDTO;
import athena.insight.biz.domain.vo.RecommendResultVO;
import athena.insight.biz.domain.vo.UserAnalysisReportVO;
import athena.insight.biz.domain.vo.UserFeatureSnapshotVO;
import athena.insight.biz.domain.vo.UserInsightVO;
import athena.insight.biz.service.AiReportNarrationService;
import athena.insight.biz.service.RecommendationService;
import athena.insight.biz.service.ReportService;
import athena.insight.biz.service.UserFeatureService;
import athena.insight.biz.service.UserInsightService;
import athena.insight.biz.util.JsonHelper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ReportServiceImpl implements ReportService {

    private static final String SUMMARY_SOURCE_RULE = "RULE";
    private static final String SUMMARY_SOURCE_AI = "AI";

    @Resource
    private UserFeatureService userFeatureService;

    @Resource
    private UserInsightService userInsightService;

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private AiReportNarrationService aiReportNarrationService;

    @Override
    public UserAnalysisReportVO generateReport(ReportQueryDTO request) {
        UserAnalysisReportVO vo = new UserAnalysisReportVO();
        if (request == null || request.getUserId() == null) {
            vo.setSummary("暂无可用的用户报告数据");
            vo.setSummarySource(SUMMARY_SOURCE_RULE);
            vo.setHealthFocuses(Collections.emptyList());
            vo.setContentFocuses(Collections.emptyList());
            vo.setRiskTags(Collections.emptyList());
            vo.setRecommendTopics(Collections.emptyList());
            vo.setReadingSuggestions(Collections.emptyList());
            return vo;
        }

        UserFeatureSnapshotVO featureSnapshotVO = userFeatureService.getSnapshot(request.getUserId());
        UserInsightVO insightVO = userInsightService.getInsight(request.getUserId());

        List<String> healthFocuses = JsonHelper.toStringList(JsonHelper.readTree(insightVO == null ? null : insightVO.getHealthFocusJson()));
        List<String> contentFocuses = JsonHelper.toStringList(JsonHelper.readTree(insightVO == null ? null : insightVO.getContentFocusJson()));
        List<String> riskTags = JsonHelper.toStringList(JsonHelper.readTree(insightVO == null ? null : insightVO.getRiskTagsJson()));
        List<String> recommendTopics = JsonHelper.toStringList(JsonHelper.readTree(insightVO == null ? null : insightVO.getRecommendationReasonsJson()));

        JsonNode healthNode = JsonHelper.readTree(featureSnapshotVO == null ? null : featureSnapshotVO.getHealthFeatureJson());
        vo.setCurrentModeType(JsonHelper.getInt(healthNode, "currentModeType"));
        vo.setAverageCycleLength(JsonHelper.getInt(healthNode, "averageCycleLength"));
        vo.setAverageDurationDays(JsonHelper.getInt(healthNode, "averageDurationDays"));
        vo.setHealthFocuses(healthFocuses);
        vo.setContentFocuses(contentFocuses);
        vo.setRiskTags(riskTags);
        vo.setRecommendTopics(recommendTopics);

        RecommendQueryDTO recommendQueryDTO = new RecommendQueryDTO();
        recommendQueryDTO.setType((byte) 0);
        recommendQueryDTO.setPageNum(1);
        recommendQueryDTO.setPageSize(3);
        RecommendResultVO recommendResultVO = recommendationService.recommend(request.getUserId(), recommendQueryDTO);
        vo.setReadingSuggestions(recommendResultVO == null ? Collections.emptyList() : recommendResultVO.getItems());

        String fallbackSummary = buildSummary(featureSnapshotVO, insightVO, healthFocuses, contentFocuses);
        String finalSummary = aiReportNarrationService.generateSummary(vo, fallbackSummary);
        vo.setSummary(finalSummary);
        vo.setSummarySource(Objects.equals(finalSummary, fallbackSummary) ? SUMMARY_SOURCE_RULE : SUMMARY_SOURCE_AI);
        return vo;
    }

    private String buildSummary(UserFeatureSnapshotVO featureSnapshotVO,
                                UserInsightVO insightVO,
                                List<String> healthFocuses,
                                List<String> contentFocuses) {
        boolean hasFeature = featureSnapshotVO != null && featureSnapshotVO.getUserId() != null;
        boolean hasInsight = insightVO != null && insightVO.getUserId() != null;

        if (hasFeature && hasInsight && (!healthFocuses.isEmpty() || !contentFocuses.isEmpty())) {
            return "已生成个性化分析报告，当前重点关注方向已结合近期行为与主题偏好输出";
        }
        if (hasFeature && hasInsight) {
            return "已生成基础分析报告，可继续补充健康特征与更细粒度洞察";
        }
        if (hasFeature) {
            return "已生成基础特征快照，洞察结果尚未完善";
        }
        if (hasInsight) {
            return "已生成基础洞察结果，特征快照尚未完善";
        }
        return "当前暂无足够数据生成完整报告";
    }
}
