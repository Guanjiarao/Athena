package athena.insight.biz.controller;

import athena.athenaframework.result.Result;
import athena.insight.biz.domain.dto.NoteFeatureRefreshDTO;
import athena.insight.biz.service.NoteFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "洞察内部接口")
@RestController
@RequestMapping("/athena/insight/internal")
public class InsightInternalController {

    @Resource
    private NoteFeatureService noteFeatureService;

    @Operation(summary = "按内容ID刷新内容特征")
    @PostMapping("/note-feature/refresh")
    public Result refreshNoteFeature(@RequestBody NoteFeatureRefreshDTO request) {
        if (request == null || request.getNoteId() == null) {
            return Result.fail("noteId不能为空");
        }
        log.info("[InsightInternal] 收到内容特征增量刷新请求, noteId={}", request.getNoteId());
        return Result.ok(noteFeatureService.refreshByNoteId(request.getNoteId()));
    }
}
