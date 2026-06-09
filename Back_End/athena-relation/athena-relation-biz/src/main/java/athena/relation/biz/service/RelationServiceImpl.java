package athena.relation.biz.service;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.athenaframework.mq.producer.MessageQueueProducer;
import athena.count.api.CountFeignApi;
import athena.count.api.constant.CountCounterConstants;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterValueDTO;
import athena.relation.biz.domain.dataobject.FansDO;
import athena.relation.biz.domain.dataobject.FollowDO;
import athena.relation.biz.mapper.FansDOMapper;
import athena.relation.biz.mapper.FollowDOMapper;
import athena.relation.biz.rpc.UserAuthFeignApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RelationServiceImpl implements RelationService {

    @Autowired
    private FollowDOMapper followDOMapper;

    @Autowired
    private FansDOMapper fansDOMapper;

    @Autowired
    private UserAuthFeignApi userAuthFeginApi;

    @Autowired
    private CountFeignApi countFeignApi;

    @Autowired
    private MessageQueueProducer messageQueueProducer;

    private Long getCurrentUserId() {
        return UserIdHolder.getUserId();
    }

    @Override
    public Result isfollow(Long followUserId) {
        if (followUserId == null) {
            return Result.fail("目标用户ID不能为空");
        }
        Long currentUserId = getCurrentUserId();
        FollowDO followDO = followDOMapper.selectByUserIdAndFollowUserId(currentUserId, followUserId);
        return Result.ok(followDO != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result follow(Long followUserId) {
        if (followUserId == null) {
            return Result.fail("目标用户ID不能为空");
        }
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        FollowDO existFollow = followDOMapper.selectByUserIdAndFollowUserId(currentUserId, followUserId);
        if (existFollow != null) {
            return Result.fail("已关注该用户，无需重复关注");
        }

        FollowDO followDO = new FollowDO();
        followDO.setUserId(currentUserId);
        followDO.setFollowUserId(followUserId);
        followDO.setCreateTime(LocalDateTime.now());
        followDOMapper.insert(followDO);

        FansDO fansDO = new FansDO();
        fansDO.setUserId(followUserId);
        fansDO.setFansUserId(currentUserId);
        fansDO.setCreateTime(LocalDateTime.now());
        fansDOMapper.insert(fansDO);

        sendRelationCounterDelta(currentUserId, followUserId, 1L);

        log.info("关注成功");
        return Result.ok("关注成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result unfollow(Long followUserId) {
        if (followUserId == null) {
            return Result.fail("目标用户ID不能为空");
        }
        Long currentUserId = getCurrentUserId();
        int followDel = followDOMapper.deleteByUserIdAndFollowUserId(currentUserId, followUserId);
        int fanDel = fansDOMapper.deleteByUserIdAndFansUserId(followUserId, currentUserId);

        if (followDel > 0 || fanDel > 0) {
            sendRelationCounterDelta(currentUserId, followUserId, -1L);
            return Result.ok("取消关注成功");
        } else {
            return Result.fail("未关注该用户，无需取消");
        }
    }

    @Override
    public Result followList() {
        Long currentUserId = getCurrentUserId();
        List<Long> followIdList = followDOMapper.selectFollowListByUserId(currentUserId);
        List<UserDTO> followList = userAuthFeginApi.findByUserIds(followIdList);
        return Result.ok(followList);
    }

    @Override
    public Result fanList() {
        Long currentUserId = getCurrentUserId();
        List<Long> fanIdList = fansDOMapper.selectFanListByUserId(currentUserId);
        List<UserDTO> fanList = userAuthFeginApi.findByUserIds(fanIdList);
        return Result.ok(fanList);
    }

    @Override
    public Result followCount(Long userId) {
        if (userId == null) {
            userId = getCurrentUserId();
        }
        return Result.ok(getCounter(userId, CountCounterConstants.FOLLOWING_TOTAL));
    }

    @Override
    public Result fanCount(Long userId) {
        if (userId == null) {
            userId = getCurrentUserId();
        }
        return Result.ok(getCounter(userId, CountCounterConstants.FOLLOWER_TOTAL));
    }

    private void sendRelationCounterDelta(Long currentUserId, Long followUserId, Long delta) {
        CounterDeltaDTO followingDelta = buildUserCounterDelta(currentUserId, CountCounterConstants.FOLLOWING_TOTAL, delta);
        CounterDeltaDTO followerDelta = buildUserCounterDelta(followUserId, CountCounterConstants.FOLLOWER_TOTAL, delta);
        messageQueueProducer.send(CountCounterConstants.EVENT_TOPIC, followingDelta.getEventId(), CountCounterConstants.EVENT_BIZ_DESC, followingDelta);
        messageQueueProducer.send(CountCounterConstants.EVENT_TOPIC, followerDelta.getEventId(), CountCounterConstants.EVENT_BIZ_DESC, followerDelta);
    }

    private CounterDeltaDTO buildUserCounterDelta(Long userId, String counterType, Long delta) {
        CounterDeltaDTO deltaDTO = new CounterDeltaDTO();
        deltaDTO.setScope(CountCounterConstants.SCOPE_USER);
        deltaDTO.setTargetId(userId);
        deltaDTO.setCounterType(counterType);
        deltaDTO.setDelta(delta);
        deltaDTO.setEventId(UUID.randomUUID().toString());
        return deltaDTO;
    }

    private Long getCounter(Long userId, String counterType) {
        try {
            Result<CounterValueDTO> result = countFeignApi.getOne(CountCounterConstants.SCOPE_USER, userId);
            if (result == null || result.getData() == null) {
                return fallbackUserCounter(userId, counterType);
            }
            Map<String, Long> counters = result.getData().getCounters();
            if (counters == null) {
                return fallbackUserCounter(userId, counterType);
            }
            return counters.getOrDefault(counterType, fallbackUserCounter(userId, counterType));
        } catch (Exception e) {
            log.warn("[RelationService] 读取计数中心失败, fallback DB, userId={}, counterType={}", userId, counterType, e);
            return fallbackUserCounter(userId, counterType);
        }
    }

    private Long fallbackUserCounter(Long userId, String counterType) {
        if (CountCounterConstants.FOLLOWING_TOTAL.equals(counterType)) {
            Long count = followDOMapper.selectFollowCountByUserId(userId);
            return count == null ? 0L : count;
        }
        if (CountCounterConstants.FOLLOWER_TOTAL.equals(counterType)) {
            Long count = fansDOMapper.selectFanCountByUserId(userId);
            return count == null ? 0L : count;
        }
        return 0L;
    }
}
