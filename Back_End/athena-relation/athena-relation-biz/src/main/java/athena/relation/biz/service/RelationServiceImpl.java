package athena.relation.biz.service;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
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

@Slf4j
@Service
public class RelationServiceImpl implements RelationService {

    @Autowired
    private FollowDOMapper followDOMapper;

    @Autowired
    private FansDOMapper fansDOMapper;

    @Autowired
    private UserAuthFeignApi userAuthFeginApi;

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
        Long count = followDOMapper.selectFollowCountByUserId(userId);
        return Result.ok(count == null ? 0 : count);
    }

    @Override
    public Result fanCount(Long userId) {
        if (userId == null) {
            userId = getCurrentUserId();
        }
        Long count = fansDOMapper.selectFanCountByUserId(userId);
        return Result.ok(count == null ? 0 : count);
    }
}
