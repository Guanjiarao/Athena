package athena.ground.biz.controller;


import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.ground.biz.domain.dto.BlogAskDTO;
import athena.ground.biz.domain.dto.BlogListDTO;
import athena.ground.biz.domain.dto.NoteIdListQueryDTO;
import athena.ground.biz.domain.dto.NoteSubmitDTO;
import athena.ground.biz.service.GroundService;
import athena.ground.biz.service.NoteSearchService;
import athena.ground.biz.service.ViewRecordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 广场模块控制器
 */
@Slf4j
@RestController
@RequestMapping("/athena/blog")
public class GroundController {

    // 注入Service层
    @Resource
    private GroundService groundService;

    @Resource
    private NoteSearchService noteSearchService;

    @Resource
    private ViewRecordService viewRecordService;

    /**
     * 广场博客列表查询（返回前10条）
     */
    @GetMapping("/list")
    public Result getBlogListPage(Integer pageNum,Integer pageSize) {
        // 直接调用Service并返回结果
        return groundService.getBlogListPage( pageNum, pageSize);
    }

    /**
     * 博客详情：返回所有字段
     * @param blog_id 博客ID（对应NoteDO的id）
     * @param type 博客类型
     */
    @GetMapping("/Detail")
    public Result getBlogDetail(
            @RequestParam("blog_id") Long blog_id,
            @RequestParam("type") Byte type
    ) {
        // 直接调用Service返回所有字段
        return groundService.getBlogDetail(blog_id, type);
    }

    /**
     * 根据 noteIdList 批量查询笔记基础信息
     */
    @PostMapping("/noteBasic/listByNoteIds")
    public Result getNoteBasicListByNoteIdList(@RequestBody NoteIdListQueryDTO queryDTO) {
        return groundService.getNoteBasicListByNoteIdList(queryDTO == null ? null : queryDTO.getNoteIdList());
    }


    /**
     * 上传笔记接口
     * @param noteSubmitDTO 笔记提交参数（@Valid 支持JSR303参数校验）
     * @return 上传结果（返回笔记ID）
     */
    @PostMapping("/submit")
    public Result<Long> submitNote( @RequestBody NoteSubmitDTO noteSubmitDTO) {
        return groundService.submitNote(noteSubmitDTO);
    }

    /**
     * 删除我的笔记
     */
    @DeleteMapping("/{noteId}")
    public Result deleteNote(@PathVariable("noteId") Long noteId) {
        return groundService.deleteNote(noteId);
    }

    /**
     * 博客问答接口
     */
    @PostMapping("/ask")
    public Result askBlog(@RequestBody BlogAskDTO request) {
        return groundService.askBlog(request);
    }

    /**
     * 笔记点赞
     */
    @PostMapping("/like")
    public Result likeNote(@RequestParam Long blogId) {
        return groundService.likeNote(blogId);
    }

    /**
     * 笔记收藏
     */
    @PostMapping("/collect")
    public Result collectNote(@RequestParam Long blogId) {
        return groundService.collectNote(blogId);
    }

    /**
     * 检查是否点赞过该笔记
     */
    @GetMapping("/isLike")
    public Result isLikeNote(@RequestParam Long blogId) {
        return groundService.isLikeNote(blogId);
    }

    /**
     * 检查是否收藏过该笔记
     */
    @GetMapping("/isCollect")
    public Result isCollectNote(@RequestParam Long blogId) {
        return groundService.isCollectNote(blogId);
    }

    /**
     * 我的点赞列表
     */
    @GetMapping("/likeList")
    public Result likeList() {
        return groundService.likeList();
    }

    /**
     * 我的收藏列表
     */
    @GetMapping("/collectList")
    public Result collectList() {
        return groundService.collectList();
    }

    /**
     * 增加点赞数
     */
    @PostMapping("/likeadd")
    public Result likeAdd(@RequestParam Long noteId, @RequestParam Long num) {
        return groundService.likeAdd(noteId, num);
    }

    /**
     * 增加收藏数
     */
    @PostMapping("/collectadd")
    public Result collectAdd(@RequestParam Long noteId, @RequestParam Long num) {
        return groundService.collectAdd(noteId, num);
    }

    /**
     * 增加评论数
     */
    @PostMapping("/commentadd")
    public Result commentAdd(@RequestParam Long noteId, @RequestParam Long num) {
        return groundService.commentAdd(noteId, num);
    }

    // ========== 新增：按频道ID查询笔记列表 ==========
    /**
     * 按频道ID分页查询笔记列表
     * @param channelId 频道ID（1=健身指南、2=避孕指南、3=个性化护肤、4=科学养护生理期、5=私处护理）
     * @param pageNum 页码（默认1）
     * @param pageSize 每页条数（默认10）
     */
    @GetMapping("/listBychannelId")
    public Result getBlogListByChannelId(
            @RequestParam("channelId") Integer channelId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return groundService.getBlogListByChannelId(channelId, pageNum, pageSize);
    }

    /**
     * 按笔记类型分页查询笔记列表
     * @param type 笔记类型（1-图文，2-视频等,不区分年龄段）,（3、4）表示0到12岁，(5,6)表示12～22岁，(7,8)表示(22～55)，(9，10)表示55+
     * @param pageNum 页码（默认1）
     * @param pageSize 每页条数（默认10）
     */
    @GetMapping("/listByTypeId")
    public Result getBlogListByType(
            @RequestParam("type") Integer type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return groundService.getBlogListByType(type, pageNum, pageSize);
    }

    @GetMapping("/myList")
    public Result getBlogListByUserId(
            @RequestParam(value = "userId",required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {

        return groundService.getBlogListByUserId(userId,pageNum,pageSize);
    }

    @GetMapping("/search/v1")
    public Result searchPublicNotes(
            @RequestParam("keyword") String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer channelId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return noteSearchService.searchPublicNotes(keyword, type, channelId, pageNum, pageSize);
    }

    @GetMapping("/mySearch/v1")
    public Result searchMyNotes(
            @RequestParam("keyword") String keyword,
            @RequestParam(required = false) Byte status,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return noteSearchService.searchMyNotes(keyword, status, type, pageNum, pageSize);
    }

    // ========== 浏览记录 ==========

    /**
     * 记录浏览（用户停留 ≥ 3 秒后前端调用）
     */
    @PostMapping("/view")
    public Result recordView(@RequestParam Long noteId,
                             @RequestParam(defaultValue = "0") Integer duration) {
        Long userId = UserIdHolder.getUserId();
        log.info("[浏览记录] 收到请求, userId={}, noteId={}, duration={}s", userId, noteId, duration);
        if (userId == null) {
            log.warn("[浏览记录] 用户未登录, noteId={}", noteId);
            return Result.fail("用户未登录");
        }
        Result result = viewRecordService.recordView(userId, noteId, duration);
        log.info("[浏览记录] 处理完成, userId={}, noteId={}, code={}", userId, noteId, result.getCode());
        return result;
    }

    /**
     * 最近浏览列表（游标翻页）
     * @param cursor 游标（上一页返回的 total 字段值，首页不传或传 0）
     * @param pageSize 每页条数（默认 10）
     */
    @GetMapping("/viewHistory")
    public Result viewHistory(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        Long userId = UserIdHolder.getUserId();
        log.info("[浏览历史] 收到请求, userId={}, cursor={}, pageSize={}", userId, cursor, pageSize);
        if (userId == null) {
            log.warn("[浏览历史] 用户未登录");
            return Result.fail("用户未登录");
        }
        Result result = viewRecordService.getRecentViews(userId, cursor, pageSize);
        log.info("[浏览历史] 处理完成, userId={}, code={}", userId, result.getCode());
        return result;
    }

}
