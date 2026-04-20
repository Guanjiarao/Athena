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
public class MenstruationPredictionVO {

    private Boolean predictable;

    private String reason;

    private Integer referenceCycleCount;

    private Integer durationSampleCount;

    private Integer averageCycleLength;

    private Integer averageDurationDays;

    private LocalDate predictedStartDate;

    private LocalDate predictedEndDate;
}
