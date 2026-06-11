package athena.rank.biz.service.impl;

import athena.rank.api.dto.RankItemDTO;
import athena.rank.api.dto.RankListDTO;
import athena.rank.api.dto.RankPositionDTO;
import athena.rank.api.dto.RankQueryDTO;
import athena.rank.api.dto.RankUpdateDTO;
import athena.rank.biz.constant.RankConstants;
import athena.rank.biz.model.RankContext;
import athena.rank.biz.repository.ExactRankRepository;
import athena.rank.biz.repository.SegmentTreeRankRepository;
import athena.rank.biz.service.RankService;
import athena.rank.biz.strategy.RankSceneStrategy;
import athena.rank.biz.strategy.RankSceneStrategyFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class HybridRankServiceImpl implements RankService {

    @Resource
    private RankSceneStrategyFactory strategyFactory;

    @Resource
    private ExactRankRepository exactRankRepository;

    @Resource
    private SegmentTreeRankRepository segmentTreeRankRepository;

    @Override
    public void update(RankUpdateDTO updateDTO) {
        validateUpdate(updateDTO);
        RankContext context = context(updateDTO.getScene());
        long eventTimeMillis = resolveTime(updateDTO.getEventTimeMillis());
        long periodNo = context.resolvePeriodNo(eventTimeMillis);
        String rankKey = buildRankKey(context.getScene(), periodNo);
        Long oldScore = exactRankRepository.score(rankKey, updateDTO.getMemberId());
        long oldScoreValue = oldScore == null ? 0L : oldScore;
        long newScore = oldScoreValue + updateDTO.getDelta();
        String requestId = StringUtils.hasText(updateDTO.getRequestId()) ? updateDTO.getRequestId() : UUID.randomUUID().toString();
        boolean updated = exactRankRepository.updateScoreOnce(
                requestId,
                rankKey,
                updateDTO.getMemberId(),
                updateDTO.getDelta(),
                eventTimeMillis / 1000
        );
        if (!updated) {
            return;
        }
        segmentTreeRankRepository.applyDelta(context.getScene(), periodNo, oldScoreValue, newScore);
        exactRankRepository.trim(rankKey, context.getExactCapacity());
    }

    @Override
    public RankListDTO top(RankQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("排行榜查询不能为空");
        }
        RankContext context = context(queryDTO.getScene());
        long periodNo = context.resolvePeriodNo(resolveTime(queryDTO.getPeriodTimeMillis()));
        int start = queryDTO.getStart() == null ? 0 : Math.max(0, queryDTO.getStart());
        int size = queryDTO.getSize() == null ? 20 : Math.min(Math.max(1, queryDTO.getSize()), 100);
        List<RankItemDTO> items = exactRankRepository.top(buildRankKey(context.getScene(), periodNo), start, size);
        RankListDTO listDTO = new RankListDTO();
        listDTO.setScene(context.getScene());
        listDTO.setPeriodNo(periodNo);
        listDTO.setItems(items);
        return listDTO;
    }

    @Override
    public RankPositionDTO position(String scene, Long memberId, Long periodTimeMillis) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId 不能为空");
        }
        RankContext context = context(scene);
        long periodNo = context.resolvePeriodNo(resolveTime(periodTimeMillis));
        String rankKey = buildRankKey(context.getScene(), periodNo);
        Long score = exactRankRepository.score(rankKey, memberId);
        Long rankNo = exactRankRepository.rankNo(rankKey, memberId);
        boolean estimated = false;
        if (score != null && rankNo == null) {
            rankNo = segmentTreeRankRepository.estimateBetterCount(context.getScene(), periodNo, score) + 1;
            estimated = true;
        }
        RankPositionDTO positionDTO = new RankPositionDTO();
        positionDTO.setScene(context.getScene());
        positionDTO.setPeriodNo(periodNo);
        positionDTO.setMemberId(memberId);
        positionDTO.setScore(score);
        positionDTO.setRankNo(rankNo);
        positionDTO.setEstimated(estimated);
        return positionDTO;
    }

    private RankContext context(String scene) {
        RankSceneStrategy strategy = strategyFactory.get(scene);
        return strategy.context();
    }

    private void validateUpdate(RankUpdateDTO updateDTO) {
        if (updateDTO == null) {
            throw new IllegalArgumentException("排行榜更新不能为空");
        }
        if (updateDTO.getMemberId() == null || updateDTO.getDelta() == null) {
            throw new IllegalArgumentException("memberId 和 delta 不能为空");
        }
    }

    private long resolveTime(Long timeMillis) {
        return timeMillis == null ? System.currentTimeMillis() : timeMillis;
    }

    private String buildRankKey(String scene, long periodNo) {
        return RankConstants.RANK_KEY_PREFIX + scene + ":" + periodNo;
    }
}
