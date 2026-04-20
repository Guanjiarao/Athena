package athena.ground.biz.service;

/**
 * Athena note 标准 document 上传服务
 */
public interface AthenaNoteDocumentUploadService {

    void upload(Long noteId, String title, String contentHtml, Byte type, Long authorId);

    void deleteByNoteId(Long noteId, Byte type, Long authorId);
}
