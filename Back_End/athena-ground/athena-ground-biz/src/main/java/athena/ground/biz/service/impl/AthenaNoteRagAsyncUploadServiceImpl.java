package athena.ground.biz.service.impl;

import athena.ground.biz.service.AthenaNoteDocumentUploadService;
import athena.ground.biz.service.AthenaNoteRagAsyncUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Athena note 异步上传 RAG 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaNoteRagAsyncUploadServiceImpl implements AthenaNoteRagAsyncUploadService {

    private final AthenaNoteDocumentUploadService athenaNoteDocumentUploadService;

    @Qualifier("athenaNoteRagUploadExecutor")
    private final Executor athenaNoteRagUploadExecutor;

    @Override
    public void submitAfterCommit(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        Runnable submitTask = () -> submitUploadTask(noteId, title, contentHtml, type, authorId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }
        submitTask.run();
    }

    private void submitUploadTask(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        try {
            athenaNoteRagUploadExecutor.execute(() -> uploadQuietly(noteId, title, contentHtml, type, authorId));
            log.info("[AthenaNoteUpload] 已提交异步上传 RAG 任务, noteId={}, authorId={}, type={}", noteId, authorId, type);
        } catch (RejectedExecutionException e) {
            log.error("[AthenaNoteUpload] 异步上传 RAG 线程池已满，任务被拒绝, noteId={}, authorId={}, type={}", noteId, authorId, type, e);
        }
    }

    private void uploadQuietly(Long noteId, String title, String contentHtml, Byte type, Long authorId) {
        try {
            log.info("[AthenaNoteUpload] 开始异步上传 RAG, noteId={}, authorId={}, type={}", noteId, authorId, type);
            athenaNoteDocumentUploadService.upload(noteId, title, contentHtml, type, authorId);
            log.info("[AthenaNoteUpload] 异步上传 RAG 成功, noteId={}, authorId={}, type={}", noteId, authorId, type);
        } catch (Exception e) {
            log.error("[AthenaNoteUpload] 异步上传 RAG 失败, noteId={}, authorId={}, type={}", noteId, authorId, type, e);
        }
    }
}
