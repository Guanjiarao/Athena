package athena.ground.biz.service;

/**
 * Athena 笔记知识同步派发服务
 */
public interface AthenaNoteSyncDispatchService {

    void dispatch(Long noteId, String title, String contentHtml, Byte type, Long authorId);
}
