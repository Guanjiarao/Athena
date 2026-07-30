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
public class MenstruationCycleVO {

    private Long id;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate displayEndDate;

    private Integer durationDays;

    private Integer displayDurationDays;

    private Integer cycleLength;

    private Boolean predicted;

    private LocalDate monthStartDate;

    private LocalDate monthEndDate;
}
