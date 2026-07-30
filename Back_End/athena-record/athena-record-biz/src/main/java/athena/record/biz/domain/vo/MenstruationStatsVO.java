package athena.record.biz.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenstruationStatsVO {

    private Integer averageCycleLength;

    private Integer averageDurationDays;

    private Integer cycleSampleCount;

    private Integer durationSampleCount;

    private LocalDate predictedNextStartDate;

    private LocalDate predictedNextEndDate;
}
