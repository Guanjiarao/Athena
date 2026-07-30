package athena.record.biz.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateDailyRecordDTO {

    private LocalDate recordDate;
    private Integer modeType;
    private Integer recordItemId;
    private String recordValue;
}
