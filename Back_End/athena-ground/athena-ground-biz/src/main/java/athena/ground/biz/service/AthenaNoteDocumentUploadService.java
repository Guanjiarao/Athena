package athena.ground.biz.service;

import athena.ground.biz.domain.dto.AthenaNoteDocumentUploadResult;

/**
 * Athena note 标准 document 上传服务
 */
public interface AthenaNoteDocumentUploadService {

    AthenaNoteDocumentUploadResult upload(Long noteId, String title, String contentHtml, Byte type, Long authorId);

    void deleteByNoteId(Long noteId, Byte type, Long authorId);
}
