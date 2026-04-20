package athena.insight.biz.domain.dto;

import lombok.Data;

@Data
public class RecommendQueryDTO {

    private Byte type;

    private Integer pageNum;

    private Integer pageSize;
}
