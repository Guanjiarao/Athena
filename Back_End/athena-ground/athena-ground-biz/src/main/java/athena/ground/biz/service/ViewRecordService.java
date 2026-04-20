package athena.ground.biz.service;

import athena.athenaframework.result.Result;

/**
 * 浏览记录 Service
 */
public interface ViewRecordService {

    /**
     * 记录浏览（写 Redis + 发 MQ）
     * @param duration 浏览时长（秒）
     */
    Result recordView(Long userId, Long noteId, Integer duration);

    /**
     * 查询最近浏览列表（游标翻页，返回 BlogListDTO）
     * @param userId 用户ID
     * @param cursor 游标（上一页最后一条的时间戳，首页传 null 或 0）
     * @param pageSize 每页条数
     */
    Result getRecentViews(Long userId, Long cursor, Integer pageSize);
}
