package athena.record.biz.service;

import athena.record.biz.domain.dataobject.MenstruationCycle;
import athena.record.biz.domain.dto.MenstruationStartDTO;
import athena.record.biz.domain.dto.MenstruationUpdateDTO;
import athena.record.biz.domain.vo.MenstruationCycleVO;
import athena.record.biz.domain.vo.MenstruationMonthVO;
import athena.record.biz.domain.vo.MenstruationPredictionVO;
import athena.record.biz.domain.vo.MenstruationStatsVO;

import java.time.LocalDate;

public interface MenstruationCycleService {

    MenstruationCycleVO startMenstruation(Long userId, MenstruationStartDTO dto);

    MenstruationCycleVO updateMenstruation(Long userId, Long id, MenstruationUpdateDTO dto);

    void deleteMenstruation(Long userId, Long id);

    MenstruationCycleVO getLatestCycle(Long userId);

    MenstruationStatsVO getCycleStats(Long userId);

    MenstruationMonthVO getMonthView(Long userId, int year, int month);

    MenstruationPredictionVO getPrediction(Long userId);

    MenstruationCycle findCycleByDate(Long userId, LocalDate recordDate);
}
