package athena.record.biz.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MenstruationStartDTO {

    private LocalDate startDate;

    private LocalDate endDate;
}
