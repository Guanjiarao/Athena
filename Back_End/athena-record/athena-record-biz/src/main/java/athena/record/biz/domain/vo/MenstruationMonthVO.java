package athena.record.biz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenstruationMonthVO {

    private Integer year;

    private Integer month;

    private List<MenstruationCycleVO> actualCycleList;

    private List<MenstruationCycleVO> predictedCycleList;

    private List<LocalDate> actualDates;

    private List<LocalDate> predictedDates;

    private Boolean todayInActualCycle;

    private Boolean todayInPredictedCycle;

    private LocalDate nextPredictedStartDate;

    private LocalDate nextPredictedEndDate;
}
