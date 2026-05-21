

package com.nageoffer.ai.ragent.rag.controller;





import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.controller.request.QueryTermMappingCreateRequest;
import com.nageoffer.ai.ragent.rag.controller.request.QueryTermMappingPageRequest;
import com.nageoffer.ai.ragent.rag.controller.request.QueryTermMappingUpdateRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.QueryTermMappingVO;
import com.nageoffer.ai.ragent.rag.service.QueryTermMappingAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关键词映射管理控制器
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG接口")
public class QueryTermMappingController {

    private final QueryTermMappingAdminService queryTermMappingAdminService;

    /**
     * 分页查询映射规则
     */
    @GetMapping("/mappings")
    @Operation(summary = "分页查询映射规则")
    public Result<IPage<QueryTermMappingVO>> pageQuery(QueryTermMappingPageRequest requestParam) {
        return Results.success(queryTermMappingAdminService.pageQuery(requestParam));
    }

    /**
     * 查询映射规则详情
     */
    @GetMapping("/mappings/{id}")
    @Operation(summary = "查询映射规则详情")
    public Result<QueryTermMappingVO> queryById(@PathVariable String id) {
        return Results.success(queryTermMappingAdminService.queryById(id));
    }

    /**
     * 创建映射规则
     */
    @PostMapping("/mappings")
    @Operation(summary = "创建映射规则")
    public Result<String> create(@RequestBody QueryTermMappingCreateRequest requestParam) {
        return Results.success(queryTermMappingAdminService.create(requestParam));
    }

    /**
     * 更新映射规则
     */
    @PutMapping("/mappings/{id}")
    @Operation(summary = "更新映射规则")
    public Result<Void> update(@PathVariable String id, @RequestBody QueryTermMappingUpdateRequest requestParam) {
        queryTermMappingAdminService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除映射规则
     */
    @DeleteMapping("/mappings/{id}")
    @Operation(summary = "删除映射规则")
    public Result<Void> delete(@PathVariable String id) {
        queryTermMappingAdminService.delete(id);
        return Results.success();
    }
}
