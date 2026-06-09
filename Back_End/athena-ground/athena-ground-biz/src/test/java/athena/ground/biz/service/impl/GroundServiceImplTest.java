package athena.ground.biz.service.impl;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.count.api.CountFeignApi;
import athena.count.api.constant.CountCounterConstants;
import athena.count.api.dto.CounterValueDTO;
import athena.ground.biz.AthenaGroundApplication;
import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteContentDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dataobject.NoteDO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteContentDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.domain.mapper.NoteDOMapper;
import athena.ground.biz.rpc.UserAuthFeignApi;
import athena.ground.biz.service.GroundService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AthenaGroundApplication.class)
class GroundServiceImplTest {

    private static final int WARM_UP_TIMES = 2;
    private static final int MEASURE_TIMES = 5;

    @Resource
    private GroundService groundService;

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private NoteDOMapper noteMapper;

    @Resource
    private NoteContentDOMapper noteContentDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Resource
    private UserAuthFeignApi userAuthFeginApi;

    @Resource
    private CountFeignApi countFeignApi;

    @Test
    void estimateRealCostBetweenSerialAndAsyncBlogDetail() {
        Long noteId = findRealApprovedNoteId();
        Assumptions.assumeTrue(noteId != null, "当前连接的 MySQL 中没有 status=1 的真实笔记，跳过耗时估算");

        for (int i = 0; i < WARM_UP_TIMES; i++) {
            serialBlogDetailAggregation(noteId);
            Result asyncResult = groundService.getBlogDetail(noteId);
            assertThat(asyncResult.getCode()).isEqualTo(200);
        }

        long serialTotalMillis = 0L;
        long asyncTotalMillis = 0L;
        for (int i = 0; i < MEASURE_TIMES; i++) {
            serialTotalMillis += costMillis(() -> serialBlogDetailAggregation(noteId));
            asyncTotalMillis += costMillis(() -> {
                Result result = groundService.getBlogDetail(noteId);
                assertThat(result.getCode()).isEqualTo(200);
            });
        }

        double serialAvgMillis = serialTotalMillis * 1.0 / MEASURE_TIMES;
        double asyncAvgMillis = asyncTotalMillis * 1.0 / MEASURE_TIMES;
        double savedMillis = serialAvgMillis - asyncAvgMillis;
        double savedPercent = serialAvgMillis <= 0 ? 0 : savedMillis * 100.0 / serialAvgMillis;

        System.out.printf(
                "真实配置笔记详情耗时估算：noteId=%d, 串行平均=%.2fms, 异步平均=%.2fms, 节省≈%.2fms, 提升≈%.2f%%%n",
                noteId, serialAvgMillis, asyncAvgMillis, savedMillis, savedPercent
        );

        assertThat(asyncAvgMillis).isGreaterThanOrEqualTo(0D);
    }

    private Long findRealApprovedNoteId() {
        List<NoteBasicDO> approvedNotes = noteBasicMapper.selectApprovedByType(1, 0, 1);
        if (approvedNotes == null || approvedNotes.isEmpty()) {
            return null;
        }
        return approvedNotes.get(0).getNoteId();
    }

    private void serialBlogDetailAggregation(Long noteId) {
        NoteBasicDO noteBasicDO = noteBasicMapper.selectApprovedByNoteId(noteId);
        assertThat(noteBasicDO).isNotNull();

        NoteDO noteDO = noteMapper.selectByPrimaryKey(noteId);
        NoteContentDO noteContentDO = noteContentDOMapper.selectByNoteId(noteId);
        NoteCountDO fallbackNoteCountDO = noteCountDOMapper.selectByNoteId(noteId);
        Result<CounterValueDTO> counterResult = getCounterWithFallback(noteId);
        UserDTO userDTO = noteDO == null || noteDO.getUserId() == null ? null : getUserWithFallback(noteDO.getUserId());

        assertThat(noteDO).isNotNull();
        assertThat(noteContentDO).isNotNull();
        assertThat(counterResult).isNotNull();
        if (counterResult.getData() == null || counterResult.getData().getCounters() == null) {
            assertThat(fallbackNoteCountDO).isNotNull();
        }
    }

    private Result<CounterValueDTO> getCounterWithFallback(Long noteId) {
        try {
            return countFeignApi.getOne(CountCounterConstants.SCOPE_NOTE, noteId);
        } catch (Exception e) {
            return Result.ok();
        }
    }

    private UserDTO getUserWithFallback(Long userId) {
        try {
            return userAuthFeginApi.findByUserId(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private long costMillis(Runnable runnable) {
        long startNanoTime = System.nanoTime();
        runnable.run();
        return (System.nanoTime() - startNanoTime) / 1_000_000;
    }
}
