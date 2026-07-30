package athena.relation.biz.service;

import athena.athenaframework.result.Result;

public interface RelationService {
    //我关注了吗
    public Result isfollow(Long followUserId);

    //关注
    public Result follow(  Long followUserId);
    //取关
    public Result unfollow(  Long followUserId);

    //关注有啥人
    public Result followList();

    //粉丝有啥人
    public Result fanList();

    //关注的数量
    public Result followCount(  Long userId);
    //粉丝的数量
    public Result fanCount(  Long userId);
}
