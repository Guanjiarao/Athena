package athena.ground.biz.rpc;

import athena.athenaframework.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "athena-comment")
public interface CommentFeignApi {

    @DeleteMapping("/athena/comment/by-note")
    Result<?> deleteByNoteId(@RequestParam("noteId") Long noteId);
}
