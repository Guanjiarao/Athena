package athena.ground.biz.service;

/**
 * Athena note 异步上传 RAG 服务
 */
public interface AthenaNoteRagAsyncUploadService {

    void submitAfterCommit(Long noteId, String title, String contentHtml, Byte type, Long authorId);
}
