package athena.relation.biz.controller;

import athena.athenaframework.result.Result;
import athena.relation.biz.service.RelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/athena/relation")
public class RelationController {

    @Autowired
    private RelationService relationService;

    /**
     * 查询是否关注了目标用户
     * @param followUserId 目标用户ID
     */
    @GetMapping("/isFollow")
    public Result isfollow(@RequestParam Long followUserId) {
        return relationService.isfollow(followUserId);
    }

    /**
     * 关注用户
     * @param followUserId 目标用户ID
     */
    @PostMapping("/follow")
    public Result follow(@RequestParam Long followUserId) {
        return relationService.follow(followUserId);
    }

    /**
     * 取消关注
     * @param followUserId 目标用户ID
     */
    @PostMapping("/unfollow")
    public Result unfollow(@RequestParam Long followUserId) {
        return relationService.unfollow(followUserId);
    }

    /**
     * 查询我的关注列表
     */
    @GetMapping("/followList")
    public Result followList() {
        return relationService.followList();
    }

    /**
     * 查询我的粉丝列表
     */
    @GetMapping("/fanList")
    public Result fanList() {
        return relationService.fanList();
    }

    /**
     * 查询关注数量（可传用户ID，不传则查当前用户）
     * @param userId 用户ID（可选）
     */
    @GetMapping("/followCount")
    public Result followCount(@RequestParam(required = false) Long userId) {
        return relationService.followCount(userId);
    }

    /**
     * 查询粉丝数量（可传用户ID，不传则查当前用户）
     * @param userId 用户ID（可选）
     */
    @GetMapping("/fanCount")
    public Result fanCount(@RequestParam(required = false) Long userId) {
        return relationService.fanCount(userId);
    }
}